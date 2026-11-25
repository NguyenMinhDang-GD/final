const { WebSocketServer } = require("ws");
const jwt = require("jsonwebtoken");
const { JWT_SECRET } = require("../middleware/auth.middleware");

/**
 * Manages Realtime WebSocket clients (/ws?token=<jwt>)
 * Broadcasts ORDER_CREATED, ORDER_UPDATED, STORE_UPDATED, CATALOG_UPDATED
 * directly to Android Customer & Merchant screens.
 */
class WsHub {
  constructor() {
    this.wss = null;
    this.clients = new Map(); // ws -> { userId, role }
  }

  attach(httpServer) {
    this.wss = new WebSocketServer({ server: httpServer, path: "/ws" });

    this.wss.on("connection", (ws, req) => {
      let meta = { userId: null, role: "GUEST" };
      try {
        const url = new URL(req.url, "http://localhost");
        const token = url.searchParams.get("token");
        if (token) {
          const decoded = jwt.verify(token, JWT_SECRET());
          meta = { userId: decoded.sub, role: decoded.role };
        }
      } catch (_) {
        // Keep guest metadata if token not provided or expired
      }

      this.clients.set(ws, meta);
      ws.send(JSON.stringify({ type: "CONNECTED", role: meta.role }));

      ws.on("close", () => {
        this.clients.delete(ws);
      });
      ws.on("error", () => {
        this.clients.delete(ws);
      });
    });
  }

  broadcastAll(type, payload = {}) {
    const raw = JSON.stringify({ type, payload, timestamp: new Date().toISOString() });
    for (const [ws] of this.clients.entries()) {
      if (ws.readyState === ws.OPEN) {
        ws.send(raw);
      }
    }
  }

  broadcastOrderEvent(type, order) {
    const raw = JSON.stringify({ type, order, timestamp: new Date().toISOString() });
    for (const [ws, meta] of this.clients.entries()) {
      if (ws.readyState !== ws.OPEN) continue;
      // Deliver to all MERCHANT terminals, the owning CUSTOMER, or demo listeners
      if (
        meta.role === "MERCHANT" ||
        meta.userId === order.customer_id ||
        meta.role === "GUEST"
      ) {
        ws.send(raw);
      }
    }
  }
}

module.exports = { WsHub };
