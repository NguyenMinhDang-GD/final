const express = require("express");
const crypto = require("node:crypto");
const { authenticateJwt, requireMerchant } = require("../middleware/auth.middleware");
const { formatStoreSettings, formatSchedule } = require("../utils/serializers");

function createStoreRouter(db, wsHub) {
  const router = express.Router();

  // GET /api/store
  router.get("/", (_req, res) => {
    const settingsRow = db.prepare("SELECT * FROM store_settings WHERE id = 1").get();
    const schedules = db.prepare("SELECT * FROM store_schedules").all().map(formatSchedule);
    return res.json({
      storeSettings: formatStoreSettings(settingsRow),
      storeSchedules: schedules
    });
  });

  // PATCH /api/store/status (Quick switch: OPEN | CLOSED | Busy timer)
  router.patch("/status", authenticateJwt, requireMerchant, (req, res) => {
    const manualStatus = req.body.manual_status === "CLOSED" ? "CLOSED" : "OPEN";
    const busyMinutes = req.body.busy_duration_minutes != null ? Number(req.body.busy_duration_minutes) : null;
    const busyUntil =
      busyMinutes && busyMinutes > 0
        ? new Date(Date.now() + busyMinutes * 60 * 1000).toISOString()
        : null;

    db.prepare("UPDATE store_settings SET manual_status = ?, busy_until = ? WHERE id = 1").run(
      manualStatus,
      busyUntil
    );

    const updated = formatStoreSettings(db.prepare("SELECT * FROM store_settings WHERE id = 1").get());
    wsHub.broadcastAll("STORE_UPDATED", updated);
    return res.json({ storeSettings: updated });
  });

  // PUT /api/store/schedules/weekly
  router.put("/schedules/weekly", authenticateJwt, requireMerchant, (req, res) => {
    const dayOfWeek = Number(req.body.day_of_week);
    const openTime = String(req.body.open_time || "06:30").slice(0, 5);
    const closeTime = String(req.body.close_time || "22:00").slice(0, 5);
    const isClosed = req.body.is_closed ? 1 : 0;

    const existing = db
      .prepare("SELECT id FROM store_schedules WHERE type = 'WEEKLY' AND day_of_week = ?")
      .get(dayOfWeek);

    if (existing) {
      db.prepare(
        "UPDATE store_schedules SET open_time = ?, close_time = ?, is_closed = ? WHERE id = ?"
      ).run(openTime, closeTime, isClosed, existing.id);
    } else {
      db.prepare(`
        INSERT INTO store_schedules (id, type, day_of_week, specific_date, open_time, close_time, is_closed)
        VALUES (?, 'WEEKLY', ?, NULL, ?, ?, ?)
      `).run(crypto.randomUUID(), dayOfWeek, openTime, closeTime, isClosed);
    }

    const schedules = db.prepare("SELECT * FROM store_schedules").all().map(formatSchedule);
    wsHub.broadcastAll("STORE_UPDATED", {});
    return res.json({ storeSchedules: schedules });
  });

  // POST /api/store/schedules/special
  router.post("/schedules/special", authenticateJwt, requireMerchant, (req, res) => {
    const specificDate = String(req.body.specific_date || "").trim();
    const openTime = String(req.body.open_time || "08:00").slice(0, 5);
    const closeTime = String(req.body.close_time || "20:00").slice(0, 5);
    const isClosed = req.body.is_closed ? 1 : 0;

    if (!specificDate) {
      return res.status(400).json({ error: "Ngày đặc biệt không hợp lệ." });
    }

    const existing = db
      .prepare("SELECT id FROM store_schedules WHERE type = 'SPECIAL' AND specific_date = ?")
      .get(specificDate);

    if (existing) {
      db.prepare(
        "UPDATE store_schedules SET open_time = ?, close_time = ?, is_closed = ? WHERE id = ?"
      ).run(openTime, closeTime, isClosed, existing.id);
    } else {
      db.prepare(`
        INSERT INTO store_schedules (id, type, day_of_week, specific_date, open_time, close_time, is_closed)
        VALUES (?, 'SPECIAL', NULL, ?, ?, ?, ?)
      `).run(crypto.randomUUID(), specificDate, openTime, closeTime, isClosed);
    }

    const schedules = db.prepare("SELECT * FROM store_schedules").all().map(formatSchedule);
    wsHub.broadcastAll("STORE_UPDATED", {});
    return res.json({ storeSchedules: schedules });
  });

  // DELETE /api/store/schedules/:id
  router.delete("/schedules/:id", authenticateJwt, requireMerchant, (req, res) => {
    db.prepare("DELETE FROM store_schedules WHERE id = ?").run(req.params.id);
    const schedules = db.prepare("SELECT * FROM store_schedules").all().map(formatSchedule);
    wsHub.broadcastAll("STORE_UPDATED", {});
    return res.json({ storeSchedules: schedules });
  });

  return router;
}

module.exports = { createStoreRouter };
