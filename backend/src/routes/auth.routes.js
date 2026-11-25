const express = require("express");
const crypto = require("node:crypto");
const bcrypt = require("bcryptjs");
const { signUserToken, authenticateJwt } = require("../middleware/auth.middleware");
const { formatUser } = require("../utils/serializers");

function createAuthRouter(db) {
  const router = express.Router();

  // POST /api/auth/register
  router.post("/register", (req, res) => {
    const email = String(req.body.email || "").trim().toLowerCase();
    const password = String(req.body.password || "");
    const fullName = String(req.body.full_name || req.body.fullName || "").trim();
    const phone = String(req.body.phone || "").trim();
    const role = req.body.role === "MERCHANT" ? "MERCHANT" : "CUSTOMER";

    if (!email || !email.includes("@") || password.length < 6) {
      return res.status(400).json({ error: "Email không hợp lệ hoặc mật khẩu dưới 6 ký tự." });
    }

    const exists = db.prepare("SELECT id FROM users WHERE email = ?").get(email);
    if (exists) {
      return res.status(409).json({ error: `Email ${email} đã tồn tại trên hệ thống.` });
    }

    const id = crypto.randomUUID();
    const passwordHash = bcrypt.hashSync(password, 10);
    const initialWallet = role === "CUSTOMER" ? 500000 : 0;
    const nowIso = new Date().toISOString();

    db.prepare(`
      INSERT INTO users (id, email, password_hash, full_name, phone, role, wallet_balance, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      id,
      email,
      passwordHash,
      fullName || email.split("@")[0],
      phone || "0909000111",
      role,
      initialWallet,
      nowIso
    );

    const row = db.prepare("SELECT * FROM users WHERE id = ?").get(id);
    const user = formatUser(row);
    const token = signUserToken(user);
    return res.status(201).json({ token, user });
  });

  // POST /api/auth/login
  router.post("/login", (req, res) => {
    const email = String(req.body.email || "").trim().toLowerCase();
    const password = String(req.body.password || "");

    const row = db.prepare("SELECT * FROM users WHERE email = ?").get(email);
    if (!row || !bcrypt.compareSync(password, row.password_hash)) {
      return res.status(401).json({ error: "Email hoặc mật khẩu không chính xác." });
    }

    const user = formatUser(row);
    const token = signUserToken(user);
    return res.json({ token, user });
  });

  // POST /api/auth/demo-login (1-tap classroom demo role switch)
  router.post("/demo-login", (req, res) => {
    const role = req.body.role === "MERCHANT" ? "MERCHANT" : "CUSTOMER";
    const demoEmail = role === "MERCHANT" ? "quan@coba.vn" : "khach@coba.vn";
    const row = db.prepare("SELECT * FROM users WHERE email = ?").get(demoEmail);
    if (!row) {
      return res.status(404).json({ error: "Không tìm thấy tài khoản demo trên server." });
    }
    const user = formatUser(row);
    const token = signUserToken(user);
    return res.json({ token, user });
  });

  // GET /api/auth/me
  router.get("/me", authenticateJwt, (req, res) => {
    const row = db.prepare("SELECT * FROM users WHERE id = ?").get(req.auth.userId);
    if (!row) return res.status(404).json({ error: "Không tìm thấy người dùng." });
    return res.json({ user: formatUser(row) });
  });

  // POST /api/auth/wallet/topup
  router.post("/wallet/topup", authenticateJwt, (req, res) => {
    const amount = Math.max(0, Math.round(Number(req.body.amount || 200000)));
    db.prepare("UPDATE users SET wallet_balance = wallet_balance + ? WHERE id = ?").run(
      amount,
      req.auth.userId
    );
    const row = db.prepare("SELECT * FROM users WHERE id = ?").get(req.auth.userId);
    return res.json({ user: formatUser(row) });
  });

  return router;
}

module.exports = { createAuthRouter };
