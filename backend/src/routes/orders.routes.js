const express = require("express");
const crypto = require("node:crypto");
const { runInTransaction } = require("../config/database");
const { authenticateJwt, requireMerchant } = require("../middleware/auth.middleware");
const { assembleOrders, getOrderById, formatUser, formatStoreSettings } = require("../utils/serializers");
const { dispatchToLalamove } = require("../services/lalamove.service");

function createOrdersRouter(db, wsHub) {
  const router = express.Router();

  // GET /api/orders (Customer gets own orders; Merchant gets all orders)
  router.get("/", authenticateJwt, (req, res) => {
    const filterUid = req.auth.role === "MERCHANT" ? null : req.auth.userId;
    return res.json({ orders: assembleOrders(db, filterUid) });
  });

  // POST /api/orders (Atomic ACID Transaction: Voucher check + Shipping fee protection + Wallet deduction)
  router.post("/", authenticateJwt, (req, res) => {
    const {
      delivery_address,
      delivery_lat,
      delivery_lng,
      distance_km,
      shipping_fee,
      voucher_code,
      payment_method,
      items
    } = req.body;

    if (!Array.isArray(items) || items.length === 0) {
      return res.status(400).json({ error: "Giỏ hàng đang trống." });
    }

    try {
      const createdOrderId = runInTransaction(db, () => {
        const user = db.prepare("SELECT * FROM users WHERE id = ?").get(req.auth.userId);
        if (!user) throw new Error("Không tìm thấy tài khoản người dùng.");

        const safeShippingFee = Math.max(0, Math.round(Number(shipping_fee || 0)));
        const subtotal = items.reduce((sum, line) => {
          const unitPrice = Math.max(0, Math.round(Number(line.unit_price_snapshot || 0)));
          const qty = Math.max(1, Math.round(Number(line.quantity || 1)));
          return sum + unitPrice * qty;
        }, 0);

        let discount = 0;
        const cleanVoucher = String(voucher_code || "").trim().toUpperCase();
        if (cleanVoucher) {
          const v = db
            .prepare("SELECT * FROM vouchers WHERE UPPER(code) = ? AND is_active = 1")
            .get(cleanVoucher);
          if (!v) throw new Error(`Mã giảm giá ${cleanVoucher} không tồn tại hoặc đã ngừng hoạt động.`);
          if (new Date(v.end_at).getTime() <= Date.now()) {
            throw new Error(`Mã giảm giá ${cleanVoucher} đã hết hạn.`);
          }
          if (subtotal < Number(v.min_order_value)) {
            throw new Error(`Đơn hàng chưa đạt giá trị tối thiểu ${v.min_order_value}đ của mã ${cleanVoucher}.`);
          }
          if (Number(v.used_count) >= Number(v.usage_limit)) {
            throw new Error(`Mã giảm giá ${cleanVoucher} đã hết lượt sử dụng.`);
          }

          const rawDiscount =
            v.discount_type === "PERCENT"
              ? Math.round((subtotal * Number(v.discount_value)) / 100)
              : Number(v.discount_value);

          // Strict Business Rule: Discount NEVER exceeds subtotal (never deducts shipping fee!)
          discount = Math.min(subtotal, Math.max(0, rawDiscount));

          db.prepare("UPDATE vouchers SET used_count = used_count + 1 WHERE id = ?").run(v.id);
        }

        const totalAmount = subtotal - discount + safeShippingFee;
        const method = payment_method === "WALLET" ? "WALLET" : "COD";
        let paymentStatus = "UNPAID";

        if (method === "WALLET") {
          if (Number(user.wallet_balance) < totalAmount) {
            throw new Error("Số dư Ví ứng dụng không đủ để thanh toán đơn hàng.");
          }
          db.prepare("UPDATE users SET wallet_balance = wallet_balance - ? WHERE id = ?").run(
            totalAmount,
            user.id
          );
          paymentStatus = "PAID";
        }

        const orderId = crypto.randomUUID();
        const nowIso = new Date().toISOString();

        db.prepare(`
          INSERT INTO orders (
            id, customer_id, voucher_code, status, delivery_address, delivery_lat, delivery_lng,
            distance_km, subtotal_amount, discount_amount, shipping_fee, total_amount,
            payment_method, payment_status, created_at
          ) VALUES (?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `).run(
          orderId,
          user.id,
          cleanVoucher || null,
          String(delivery_address || ""),
          Number(delivery_lat || 0),
          Number(delivery_lng || 0),
          Number(distance_km || 0),
          subtotal,
          discount,
          safeShippingFee,
          totalAmount,
          method,
          paymentStatus,
          nowIso
        );

        const insertItem = db.prepare(`
          INSERT INTO order_items (
            id, order_id, menu_item_id, item_name_snapshot, unit_price_snapshot,
            selected_options_snapshot, quantity, special_note
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `);

        for (const item of items) {
          insertItem.run(
            crypto.randomUUID(),
            orderId,
            item.menu_item_id || null,
            String(item.item_name_snapshot || ""),
            Math.max(0, Math.round(Number(item.unit_price_snapshot || 0))),
            JSON.stringify(item.selected_options_snapshot || []),
            Math.max(1, Math.round(Number(item.quantity || 1))),
            String(item.special_note || "").trim()
          );
        }

        return orderId;
      });

      const order = getOrderById(db, createdOrderId);
      const updatedUser = formatUser(db.prepare("SELECT * FROM users WHERE id = ?").get(req.auth.userId));
      wsHub.broadcastOrderEvent("ORDER_CREATED", order);
      return res.status(201).json({ order, user: updatedUser });
    } catch (err) {
      return res.status(400).json({ error: err.message });
    }
  });

  // POST /api/orders/:id/confirm (Merchant accepts -> PREPARING)
  router.post("/:id/confirm", authenticateJwt, requireMerchant, (req, res) => {
    const existing = db.prepare("SELECT * FROM orders WHERE id = ?").get(req.params.id);
    if (!existing) return res.status(404).json({ error: "Không tìm thấy đơn hàng." });

    db.prepare("UPDATE orders SET status = 'PREPARING' WHERE id = ?").run(req.params.id);
    const updated = getOrderById(db, req.params.id);
    wsHub.broadcastOrderEvent("ORDER_UPDATED", updated);
    return res.json({ order: updated });
  });

  // POST /api/orders/:id/reject (Atomic Reject + 100% Automatic Wallet Refund)
  router.post("/:id/reject", authenticateJwt, requireMerchant, (req, res) => {
    const reason = String(req.body.reason || "").trim() || "Quán tạm hết nguyên liệu";

    try {
      runInTransaction(db, () => {
        const ord = db.prepare("SELECT * FROM orders WHERE id = ?").get(req.params.id);
        if (!ord) throw new Error("Không tìm thấy đơn hàng.");
        if (ord.status !== "PENDING" && ord.status !== "PREPARING") {
          throw new Error("Chỉ có thể từ chối đơn khi chưa bàn giao cho tài xế.");
        }

        if (ord.voucher_code) {
          db.prepare(
            "UPDATE vouchers SET used_count = MAX(0, used_count - 1) WHERE UPPER(code) = UPPER(?)"
          ).run(ord.voucher_code);
        }

        if (ord.payment_method === "WALLET" && ord.payment_status === "PAID") {
          db.prepare("UPDATE users SET wallet_balance = wallet_balance + ? WHERE id = ?").run(
            Number(ord.total_amount),
            ord.customer_id
          );
          db.prepare(
            "UPDATE orders SET status = 'REFUNDED', payment_status = 'REFUNDED', reject_reason = ? WHERE id = ?"
          ).run(reason, ord.id);
        } else {
          db.prepare("UPDATE orders SET status = 'REJECTED', reject_reason = ? WHERE id = ?").run(
            reason,
            ord.id
          );
        }
      });

      const updated = getOrderById(db, req.params.id);
      wsHub.broadcastOrderEvent("ORDER_UPDATED", updated);
      return res.json({ order: updated });
    } catch (err) {
      return res.status(400).json({ error: err.message });
    }
  });

  // POST /api/orders/:id/dispatch-shipper (1-Tap Lalamove Sandbox / Mock Dispatch)
  router.post("/:id/dispatch-shipper", authenticateJwt, requireMerchant, async (req, res) => {
    const order = getOrderById(db, req.params.id);
    if (!order) return res.status(404).json({ error: "Không tìm thấy đơn hàng." });

    const store = formatStoreSettings(db.prepare("SELECT * FROM store_settings WHERE id = 1").get());
    let lalamoveInfo = null;
    try {
      lalamoveInfo = await dispatchToLalamove({ store, order });
    } catch (err) {
      lalamoveInfo = { mode: "FALLBACK_MOCK", error: err.message };
    }

    db.prepare("UPDATE orders SET status = 'FINDING_DRIVER' WHERE id = ?").run(order.id);
    const updated = getOrderById(db, order.id);
    wsHub.broadcastOrderEvent("ORDER_UPDATED", updated);
    return res.json({ order: updated, lalamove: lalamoveInfo });
  });

  // PATCH /api/orders/:id/milestone (Mock Shipper / Driver Milestone Update)
  router.patch("/:id/milestone", authenticateJwt, (req, res) => {
    const ord = db.prepare("SELECT * FROM orders WHERE id = ?").get(req.params.id);
    if (!ord) return res.status(404).json({ error: "Không tìm thấy đơn hàng." });

    const newStatus = String(req.body.status || ord.status);
    const driverName = req.body.driver_name ?? ord.driver_name;
    const driverPhone = req.body.driver_phone ?? ord.driver_phone;
    const driverPlate = req.body.driver_plate ?? ord.driver_plate;
    const pickedUpAt =
      newStatus === "PICKED_UP" && !ord.picked_up_at ? new Date().toISOString() : ord.picked_up_at;

    db.prepare(`
      UPDATE orders
      SET status = ?, driver_name = ?, driver_phone = ?, driver_plate = ?, picked_up_at = ?
      WHERE id = ?
    `).run(newStatus, driverName, driverPhone, driverPlate, pickedUpAt, ord.id);

    const updated = getOrderById(db, ord.id);
    wsHub.broadcastOrderEvent("ORDER_UPDATED", updated);
    return res.json({ order: updated });
  });

  // POST /api/orders/:id/review (Customer 1-5 Star Rating)
  router.post("/:id/review", authenticateJwt, (req, res) => {
    const ord = db.prepare("SELECT * FROM orders WHERE id = ?").get(req.params.id);
    if (!ord) return res.status(404).json({ error: "Không tìm thấy đơn hàng." });
    if (ord.customer_id !== req.auth.userId) {
      return res.status(403).json({ error: "Bạn chỉ có thể đánh giá đơn hàng của chính mình." });
    }
    if (ord.status !== "COMPLETED") {
      return res.status(400).json({ error: "Chỉ có thể đánh giá khi đơn đã giao Hoàn thành." });
    }

    const rating = Math.min(5, Math.max(1, Math.round(Number(req.body.rating || 5))));
    const comment = String(req.body.comment || "").trim();
    const nowIso = new Date().toISOString();

    db.prepare("DELETE FROM reviews WHERE order_id = ?").run(ord.id);
    db.prepare(`
      INSERT INTO reviews (id, order_id, customer_id, rating, comment, created_at)
      VALUES (?, ?, ?, ?, ?, ?)
    `).run(crypto.randomUUID(), ord.id, req.auth.userId, rating, comment, nowIso);

    const updated = getOrderById(db, ord.id);
    wsHub.broadcastOrderEvent("ORDER_UPDATED", updated);
    return res.status(201).json({ order: updated });
  });

  return router;
}

module.exports = { createOrdersRouter };
