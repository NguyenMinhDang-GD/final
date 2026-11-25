const express = require("express");
const cors = require("cors");
const { formatStoreSettings, formatSchedule, assembleCatalog, getOrderById } = require("./utils/serializers");
const { createAuthRouter } = require("./routes/auth.routes");
const { createStoreRouter } = require("./routes/store.routes");
const { createCatalogRouter } = require("./routes/catalog.routes");
const { createOrdersRouter } = require("./routes/orders.routes");

function createApp(db, wsHub) {
  const app = express();
  app.use(cors());
  app.use(express.json({ limit: "2mb" }));

  // Healthcheck
  app.get("/api/health", (_req, res) => {
    res.json({
      status: "ok",
      service: "Cô Ba D2C F&B Node.js Server",
      engine: "Express + WebSocket + SQLite",
      timestamp: new Date().toISOString()
    });
  });

  // Single-request bootstrap payload for fast mobile startup
  app.get("/api/bootstrap", (_req, res) => {
    const storeRow = db.prepare("SELECT * FROM store_settings WHERE id = 1").get();
    const schedules = db.prepare("SELECT * FROM store_schedules").all().map(formatSchedule);
    const catalog = assembleCatalog(db);
    res.json({
      storeSettings: formatStoreSettings(storeRow),
      storeSchedules: schedules,
      ...catalog
    });
  });

  // Lalamove Webhook receiver (Public webhook endpoint)
  app.post("/api/webhooks/lalamove", (req, res) => {
    const payload = req.body || {};
    const internalOrderId = payload?.data?.order?.metadata?.internalOrderId || payload.orderId;
    const lalaStatus = payload?.data?.order?.status || payload.status;
    const driver = payload?.data?.driver || {};

    const statusMap = {
      ASSIGNING_DRIVER: "FINDING_DRIVER",
      ON_GOING: "DRIVER_AT_STORE",
      PICKED_UP: "PICKED_UP",
      IN_TRANSIT: "DELIVERING",
      COMPLETED: "COMPLETED"
    };

    const mappedStatus = statusMap[lalaStatus];
    if (internalOrderId && mappedStatus) {
      const existing = db.prepare("SELECT * FROM orders WHERE id = ?").get(internalOrderId);
      if (existing) {
        const pickedUpAt =
          mappedStatus === "PICKED_UP" && !existing.picked_up_at
            ? new Date().toISOString()
            : existing.picked_up_at;
        db.prepare(`
          UPDATE orders
          SET status = ?, driver_name = ?, driver_phone = ?, driver_plate = ?, picked_up_at = ?
          WHERE id = ?
        `).run(
          mappedStatus,
          driver.name || existing.driver_name,
          driver.phone || existing.driver_phone,
          driver.plateNumber || existing.driver_plate,
          pickedUpAt,
          internalOrderId
        );
        const updated = getOrderById(db, internalOrderId);
        wsHub.broadcastOrderEvent("ORDER_UPDATED", updated);
      }
    }
    res.json({ ok: true });
  });

  app.use("/api/auth", createAuthRouter(db));
  app.use("/api/store", createStoreRouter(db, wsHub));
  app.use("/api/catalog", createCatalogRouter(db, wsHub));
  app.use("/api/orders", createOrdersRouter(db, wsHub));

  return app;
}

module.exports = { createApp };
