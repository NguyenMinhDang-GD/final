require("dotenv").config();
const http = require("node:http");
const { getDb } = require("./config/database");
const { WsHub } = require("./realtime/wsHub");
const { createApp } = require("./app");

const PORT = Number(process.env.PORT || 3000);
const db = getDb();
const wsHub = new WsHub();
const app = createApp(db, wsHub);
const server = http.createServer(app);

wsHub.attach(server);

server.listen(PORT, "0.0.0.0", () => {
  console.log(`=========================================================`);
  console.log(`🚀 Cô Ba D2C F&B Node.js Server đang chạy tại cổng ${PORT}`);
  console.log(`   • REST API : http://localhost:${PORT}/api/health`);
  console.log(`   • Emulator : http://10.0.2.2:${PORT}/api/bootstrap`);
  console.log(`   • WebSocket: ws://10.0.2.2:${PORT}/ws`);
  console.log(`=========================================================`);
});
