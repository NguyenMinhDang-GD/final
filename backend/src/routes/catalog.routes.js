const express = require("express");
const crypto = require("node:crypto");
const { authenticateJwt, requireMerchant } = require("../middleware/auth.middleware");
const { assembleCatalog } = require("../utils/serializers");

function createCatalogRouter(db, wsHub) {
  const router = express.Router();

  // GET /api/catalog (Categories + MenuItems with OptionGroups/Options + Vouchers)
  router.get("/", (_req, res) => {
    return res.json(assembleCatalog(db));
  });

  // Categories CRUD
  router.post("/categories", authenticateJwt, requireMerchant, (req, res) => {
    const id = req.body.id || crypto.randomUUID();
    const name = String(req.body.name || "").trim();
    const sortOrder = Number(req.body.sort_order ?? 1);
    if (!name) return res.status(400).json({ error: "Tên danh mục không được để trống." });

    const exists = db.prepare("SELECT id FROM categories WHERE id = ?").get(id);
    if (exists) {
      db.prepare("UPDATE categories SET name = ?, sort_order = ? WHERE id = ?").run(name, sortOrder, id);
    } else {
      db.prepare("INSERT INTO categories (id, name, sort_order, is_active) VALUES (?, ?, ?, 1)").run(
        id,
        name,
        sortOrder
      );
    }
    wsHub.broadcastAll("CATALOG_UPDATED");
    return res.json(assembleCatalog(db));
  });

  router.delete("/categories/:id", authenticateJwt, requireMerchant, (req, res) => {
    db.prepare("DELETE FROM categories WHERE id = ?").run(req.params.id);
    wsHub.broadcastAll("CATALOG_UPDATED");
    return res.json(assembleCatalog(db));
  });

  // Menu Items CRUD & Stock Toggle
  router.post("/menu-items", authenticateJwt, requireMerchant, (req, res) => {
    const id = req.body.id || crypto.randomUUID();
    const categoryId = String(req.body.category_id || "");
    const name = String(req.body.name || "").trim();
    const description = String(req.body.description || "").trim();
    const basePrice = Math.max(0, Math.round(Number(req.body.base_price || 0)));
    const imageUrl = String(req.body.image_url || "").trim();
    const isAvailable = req.body.is_available === false ? 0 : 1;

    if (!name || !categoryId) {
      return res.status(400).json({ error: "Thiếu tên món hoặc danh mục." });
    }

    const exists = db.prepare("SELECT id FROM menu_items WHERE id = ?").get(id);
    if (exists) {
      db.prepare(`
        UPDATE menu_items
        SET category_id = ?, name = ?, description = ?, base_price = ?, image_url = ?, is_available = ?
        WHERE id = ?
      `).run(categoryId, name, description, basePrice, imageUrl, isAvailable, id);
    } else {
      db.prepare(`
        INSERT INTO menu_items (id, category_id, name, description, base_price, image_url, is_available)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `).run(id, categoryId, name, description, basePrice, imageUrl, isAvailable);
    }
    wsHub.broadcastAll("CATALOG_UPDATED");
    return res.json({ id, ...assembleCatalog(db) });
  });

  router.patch("/menu-items/:id/availability", authenticateJwt, requireMerchant, (req, res) => {
    const isAvailable = req.body.is_available ? 1 : 0;
    db.prepare("UPDATE menu_items SET is_available = ? WHERE id = ?").run(isAvailable, req.params.id);
    wsHub.broadcastAll("CATALOG_UPDATED");
    return res.json(assembleCatalog(db));
  });

  router.delete("/menu-items/:id", authenticateJwt, requireMerchant, (req, res) => {
    db.prepare("DELETE FROM menu_items WHERE id = ?").run(req.params.id);
    wsHub.broadcastAll("CATALOG_UPDATED");
    return res.json(assembleCatalog(db));
  });

  // Option Groups CRUD
  router.post("/option-groups", authenticateJwt, requireMerchant, (req, res) => {
    const id = req.body.id || crypto.randomUUID();
    const menuItemId = String(req.body.menu_item_id || "");
    const name = String(req.body.name || "").trim();
    const selectionType = req.body.selection_type === "MULTI" ? "MULTI" : "SINGLE";
    const isRequired = req.body.is_required ? 1 : 0;

    const exists = db.prepare("SELECT id FROM option_groups WHERE id = ?").get(id);
    if (exists) {
      db.prepare(
        "UPDATE option_groups SET name = ?, selection_type = ?, is_required = ? WHERE id = ?"
      ).run(name, selectionType, isRequired, id);
    } else {
      db.prepare(
        "INSERT INTO option_groups (id, menu_item_id, name, selection_type, is_required) VALUES (?, ?, ?, ?, ?)"
      ).run(id, menuItemId, name, selectionType, isRequired);
    }
    wsHub.broadcastAll("CATALOG_UPDATED");
    return res.json({ id, ...assembleCatalog(db) });
  });

  router.delete("/option-groups/:id", authenticateJwt, requireMerchant, (req, res) => {
    db.prepare("DELETE FROM option_groups WHERE id = ?").run(req.params.id);
    wsHub.broadcastAll("CATALOG_UPDATED");
    return res.json(assembleCatalog(db));
  });

  // Options CRUD
  router.post("/options", authenticateJwt, requireMerchant, (req, res) => {
    const id = req.body.id || crypto.randomUUID();
    const groupId = String(req.body.group_id || "");
    const name = String(req.body.name || "").trim();
    const extraPrice = Math.round(Number(req.body.extra_price || 0));
    const isAvailable = req.body.is_available === false ? 0 : 1;

    const exists = db.prepare("SELECT id FROM options WHERE id = ?").get(id);
    if (exists) {
      db.prepare("UPDATE options SET name = ?, extra_price = ?, is_available = ? WHERE id = ?").run(
        name,
        extraPrice,
        isAvailable,
        id
      );
    } else {
      db.prepare(
        "INSERT INTO options (id, group_id, name, extra_price, is_available) VALUES (?, ?, ?, ?, ?)"
      ).run(id, groupId, name, extraPrice, isAvailable);
    }
    wsHub.broadcastAll("CATALOG_UPDATED");
    return res.json(assembleCatalog(db));
  });

  router.delete("/options/:id", authenticateJwt, requireMerchant, (req, res) => {
    db.prepare("DELETE FROM options WHERE id = ?").run(req.params.id);
    wsHub.broadcastAll("CATALOG_UPDATED");
    return res.json(assembleCatalog(db));
  });

  // Vouchers CRUD
  router.post("/vouchers", authenticateJwt, requireMerchant, (req, res) => {
    const id = req.body.id || crypto.randomUUID();
    const code = String(req.body.code || "").trim().toUpperCase();
    const discountType = req.body.discount_type === "PERCENT" ? "PERCENT" : "FIXED";
    const discountValue = Math.max(1, Math.round(Number(req.body.discount_value || 10000)));
    const minOrderValue = Math.max(0, Math.round(Number(req.body.min_order_value || 0)));
    const usageLimit = Math.max(1, Math.round(Number(req.body.usage_limit || 50)));
    const endAt = String(req.body.end_at || new Date(Date.now() + 30 * 86400 * 1000).toISOString());
    const isActive = req.body.is_active === false ? 0 : 1;

    if (!code) return res.status(400).json({ error: "Mã voucher không được để trống." });

    const exists = db.prepare("SELECT id FROM vouchers WHERE id = ?").get(id);
    if (exists) {
      db.prepare(`
        UPDATE vouchers
        SET code = ?, discount_type = ?, discount_value = ?, min_order_value = ?, usage_limit = ?, end_at = ?, is_active = ?
        WHERE id = ?
      `).run(code, discountType, discountValue, minOrderValue, usageLimit, endAt, isActive, id);
    } else {
      db.prepare(`
        INSERT INTO vouchers (id, code, discount_type, discount_value, min_order_value, usage_limit, used_count, end_at, is_active)
        VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
      `).run(id, code, discountType, discountValue, minOrderValue, usageLimit, endAt, isActive);
    }
    wsHub.broadcastAll("CATALOG_UPDATED");
    return res.json(assembleCatalog(db));
  });

  router.patch("/vouchers/:id/active", authenticateJwt, requireMerchant, (req, res) => {
    const isActive = req.body.is_active ? 1 : 0;
    db.prepare("UPDATE vouchers SET is_active = ? WHERE id = ?").run(isActive, req.params.id);
    wsHub.broadcastAll("CATALOG_UPDATED");
    return res.json(assembleCatalog(db));
  });

  router.delete("/vouchers/:id", authenticateJwt, requireMerchant, (req, res) => {
    db.prepare("DELETE FROM vouchers WHERE id = ?").run(req.params.id);
    wsHub.broadcastAll("CATALOG_UPDATED");
    return res.json(assembleCatalog(db));
  });

  return router;
}

module.exports = { createCatalogRouter };
