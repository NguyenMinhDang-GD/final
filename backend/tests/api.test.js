const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");
const WebSocket = require("ws");
const { createDatabase } = require("../src/config/database");
const { WsHub } = require("../src/realtime/wsHub");
const { createApp } = require("../src/app");

async function startTestServer() {
  const db = createDatabase(":memory:");
  const wsHub = new WsHub();
  const app = createApp(db, wsHub);
  const server = http.createServer(app);
  wsHub.attach(server);

  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}`;
  const wsUrl = `ws://127.0.0.1:${port}/ws`;

  return {
    baseUrl,
    wsUrl,
    close: () => new Promise((resolve) => server.close(resolve))
  };
}

async function apiJson(baseUrl, path, { method = "GET", token = null, body = null } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  });
  const json = await res.json();
  return { status: res.status, json };
}

test("Node.js Server E2E: Bootstrap, JWT Auth, Voucher Cap, Wallet Deduction & Reject Refund", async () => {
  const srv = await startTestServer();
  try {
    // 1. Bootstrap returns store, schedules, categories, menu items & vouchers
    const boot = await apiJson(srv.baseUrl, "/api/bootstrap");
    assert.equal(boot.status, 200);
    assert.equal(boot.json.storeSettings.name, "Bún Riêu & Bún Chả Cá Cô Ba");
    assert.equal(boot.json.menuItems.length, 6);
    assert.equal(boot.json.vouchers.length, 3);

    // 2. Login Customer & Merchant via JWT
    const custLogin = await apiJson(srv.baseUrl, "/api/auth/login", {
      method: "POST",
      body: { email: "khach@coba.vn", password: "123456" }
    });
    assert.equal(custLogin.status, 200);
    assert.equal(custLogin.json.user.wallet_balance, 500000);
    const custToken = custLogin.json.token;

    const merchLogin = await apiJson(srv.baseUrl, "/api/auth/login", {
      method: "POST",
      body: { email: "quan@coba.vn", password: "123456" }
    });
    assert.equal(merchLogin.status, 200);
    const merchToken = merchLogin.json.token;

    // 3. Connect Merchant WebSocket to verify Realtime ORDER_CREATED push
    const wsEvents = [];
    const ws = new WebSocket(`${srv.wsUrl}?token=${merchToken}`);
    await new Promise((resolve) => ws.once("open", resolve));
    ws.on("message", (raw) => wsEvents.push(JSON.parse(raw.toString())));

    // 4. Customer places WALLET order with SIEUVIP100K (100k voucher on 55k subtotal + 20k ship)
    // Strict rule: discount MUST cap at 55,000 and shipping fee (20,000) stays intact!
    const orderRes = await apiJson(srv.baseUrl, "/api/orders", {
      method: "POST",
      token: custToken,
      body: {
        delivery_address: "Chợ Bến Thành, Quận 1",
        delivery_lat: 10.7725,
        delivery_lng: 106.698,
        distance_km: 2.4,
        shipping_fee: 20000,
        voucher_code: "SIEUVIP100K",
        payment_method: "WALLET",
        items: [
          {
            menu_item_id: "dish-bun-rieu",
            item_name_snapshot: "Bún Riêu Cua Đặc Biệt",
            unit_price_snapshot: 55000,
            selected_options_snapshot: [],
            quantity: 1,
            special_note: "Không hành, dị ứng tôm"
          }
        ]
      }
    });

    assert.equal(orderRes.status, 201);
    assert.equal(orderRes.json.order.subtotal_amount, 55000);
    assert.equal(orderRes.json.order.discount_amount, 55000);
    assert.equal(orderRes.json.order.shipping_fee, 20000);
    assert.equal(orderRes.json.order.total_amount, 20000);
    assert.equal(orderRes.json.user.wallet_balance, 480000);

    await new Promise((r) => setTimeout(r, 80));
    assert.ok(wsEvents.some((e) => e.type === "ORDER_CREATED"));

    // 5. Merchant rejects order -> Customer wallet automatically refunded back to 500,000đ!
    const orderId = orderRes.json.order.id;
    const rejectRes = await apiJson(srv.baseUrl, `/api/orders/${orderId}/reject`, {
      method: "POST",
      token: merchToken,
      body: { reason: "Hết chả cá thu" }
    });
    assert.equal(rejectRes.status, 200);
    assert.equal(rejectRes.json.order.status, "REFUNDED");
    assert.equal(rejectRes.json.order.payment_status, "REFUNDED");

    const meAfterRefund = await apiJson(srv.baseUrl, "/api/auth/me", { token: custToken });
    assert.equal(meAfterRefund.json.user.wallet_balance, 500000);

    // 6. Full Delivery Lifecycle + 5-Star Review
    const order2 = await apiJson(srv.baseUrl, "/api/orders", {
      method: "POST",
      token: custToken,
      body: {
        delivery_address: "227 Nguyễn Văn Cừ, Q5",
        delivery_lat: 10.7629,
        delivery_lng: 106.6822,
        distance_km: 1.0,
        shipping_fee: 15000,
        voucher_code: "",
        payment_method: "COD",
        items: [
          {
            menu_item_id: "dish-bun-cha-ca",
            item_name_snapshot: "Bún Chả Cá Nha Trang",
            unit_price_snapshot: 50000,
            selected_options_snapshot: [],
            quantity: 1,
            special_note: ""
          }
        ]
      }
    });
    const ord2Id = order2.json.order.id;

    await apiJson(srv.baseUrl, `/api/orders/${ord2Id}/confirm`, { method: "POST", token: merchToken });
    await apiJson(srv.baseUrl, `/api/orders/${ord2Id}/dispatch-shipper`, {
      method: "POST",
      token: merchToken
    });
    await apiJson(srv.baseUrl, `/api/orders/${ord2Id}/milestone`, {
      method: "PATCH",
      token: merchToken,
      body: {
        status: "COMPLETED",
        driver_name: "Nguyễn Văn Tài",
        driver_phone: "0909123456",
        driver_plate: "59X3-686.88"
      }
    });

    const revRes = await apiJson(srv.baseUrl, `/api/orders/${ord2Id}/review`, {
      method: "POST",
      token: custToken,
      body: { rating: 5, comment: "Rất ngon!" }
    });
    assert.equal(revRes.status, 201);
    assert.equal(revRes.json.order.review.rating, 5);

    ws.close();
  } finally {
    await srv.close();
  }
});
