const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const { DatabaseSync } = require("node:sqlite");
const bcrypt = require("bcryptjs");

let dbInstance = null;

function createDatabase(customFilePath) {
  const targetPath =
    customFilePath ||
    process.env.DB_PATH ||
    path.join(__dirname, "..", "..", "data", "coba_fnb.sqlite");

  if (targetPath !== ":memory:") {
    fs.mkdirSync(path.dirname(path.resolve(targetPath)), { recursive: true });
  }

  const db = new DatabaseSync(targetPath === ":memory:" ? ":memory:" : path.resolve(targetPath));
  db.exec("PRAGMA foreign_keys = ON;");
  if (targetPath !== ":memory:") {
    db.exec("PRAGMA journal_mode = WAL;");
  }

  initSchema(db);
  seedIfEmpty(db);
  return db;
}

function getDb() {
  if (!dbInstance) {
    dbInstance = createDatabase();
  }
  return dbInstance;
}

function runInTransaction(db, fn) {
  db.exec("BEGIN IMMEDIATE;");
  try {
    const result = fn();
    db.exec("COMMIT;");
    return result;
  } catch (err) {
    try {
      db.exec("ROLLBACK;");
    } catch (_) {
      // Ignore rollback errors if already rolled back
    }
    throw err;
  }
}

function initSchema(db) {
  db.exec(`
    -- 1. USERS / PROFILES (Role CUSTOMER | MERCHANT + Prepaid Wallet)
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      email TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      full_name TEXT NOT NULL,
      phone TEXT NOT NULL DEFAULT '',
      role TEXT NOT NULL DEFAULT 'CUSTOMER' CHECK (role IN ('CUSTOMER', 'MERCHANT')),
      wallet_balance INTEGER NOT NULL DEFAULT 500000 CHECK (wallet_balance >= 0),
      created_at TEXT NOT NULL
    );

    -- 2. STORE_SETTINGS (Single-store configuration, fixed id = 1)
    CREATE TABLE IF NOT EXISTS store_settings (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      name TEXT NOT NULL,
      address TEXT NOT NULL,
      lat REAL NOT NULL,
      lng REAL NOT NULL,
      manual_status TEXT NOT NULL DEFAULT 'OPEN' CHECK (manual_status IN ('OPEN', 'CLOSED')),
      busy_until TEXT
    );

    -- 3. STORE_SCHEDULES (WEEKLY Mon-Sun & SPECIAL holiday dates)
    CREATE TABLE IF NOT EXISTS store_schedules (
      id TEXT PRIMARY KEY,
      type TEXT NOT NULL CHECK (type IN ('WEEKLY', 'SPECIAL')),
      day_of_week INTEGER,
      specific_date TEXT,
      open_time TEXT NOT NULL DEFAULT '06:30',
      close_time TEXT NOT NULL DEFAULT '22:00',
      is_closed INTEGER NOT NULL DEFAULT 0
    );

    -- 4. CATEGORIES
    CREATE TABLE IF NOT EXISTS categories (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      sort_order INTEGER NOT NULL DEFAULT 0,
      is_active INTEGER NOT NULL DEFAULT 1
    );

    -- 5. MENU_ITEMS
    CREATE TABLE IF NOT EXISTS menu_items (
      id TEXT PRIMARY KEY,
      category_id TEXT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      description TEXT NOT NULL DEFAULT '',
      base_price INTEGER NOT NULL CHECK (base_price >= 0),
      image_url TEXT NOT NULL DEFAULT '',
      is_available INTEGER NOT NULL DEFAULT 1
    );

    -- 6. OPTION_GROUPS (SINGLE required | MULTI optional)
    CREATE TABLE IF NOT EXISTS option_groups (
      id TEXT PRIMARY KEY,
      menu_item_id TEXT NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      selection_type TEXT NOT NULL CHECK (selection_type IN ('SINGLE', 'MULTI')),
      is_required INTEGER NOT NULL DEFAULT 0
    );

    -- 7. OPTIONS (Supports positive & negative extra_price e.g. Kid bowl -5000)
    CREATE TABLE IF NOT EXISTS options (
      id TEXT PRIMARY KEY,
      group_id TEXT NOT NULL REFERENCES option_groups(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      extra_price INTEGER NOT NULL DEFAULT 0,
      is_available INTEGER NOT NULL DEFAULT 1
    );

    -- 8. VOUCHERS
    CREATE TABLE IF NOT EXISTS vouchers (
      id TEXT PRIMARY KEY,
      code TEXT NOT NULL UNIQUE,
      discount_type TEXT NOT NULL CHECK (discount_type IN ('PERCENT', 'FIXED')),
      discount_value INTEGER NOT NULL CHECK (discount_value > 0),
      min_order_value INTEGER NOT NULL DEFAULT 0,
      usage_limit INTEGER NOT NULL DEFAULT 100 CHECK (usage_limit > 0),
      used_count INTEGER NOT NULL DEFAULT 0 CHECK (used_count >= 0 AND used_count <= usage_limit),
      end_at TEXT NOT NULL,
      is_active INTEGER NOT NULL DEFAULT 1
    );

    -- 9. ORDERS
    CREATE TABLE IF NOT EXISTS orders (
      id TEXT PRIMARY KEY,
      customer_id TEXT NOT NULL REFERENCES users(id),
      voucher_code TEXT,
      status TEXT NOT NULL DEFAULT 'PENDING' CHECK (
        status IN (
          'PENDING',
          'PREPARING',
          'REJECTED',
          'REFUNDED',
          'FINDING_DRIVER',
          'DRIVER_AT_STORE',
          'PICKED_UP',
          'DELIVERING',
          'COMPLETED'
        )
      ),
      delivery_address TEXT NOT NULL,
      delivery_lat REAL NOT NULL,
      delivery_lng REAL NOT NULL,
      distance_km REAL NOT NULL DEFAULT 0,
      subtotal_amount INTEGER NOT NULL CHECK (subtotal_amount >= 0),
      discount_amount INTEGER NOT NULL DEFAULT 0 CHECK (
        discount_amount >= 0 AND discount_amount <= subtotal_amount
      ),
      shipping_fee INTEGER NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
      total_amount INTEGER NOT NULL CHECK (total_amount >= 0),
      payment_method TEXT NOT NULL CHECK (payment_method IN ('COD', 'WALLET')),
      payment_status TEXT NOT NULL CHECK (payment_status IN ('UNPAID', 'PAID', 'REFUNDED')),
      reject_reason TEXT,
      driver_name TEXT,
      driver_phone TEXT,
      driver_plate TEXT,
      picked_up_at TEXT,
      created_at TEXT NOT NULL
    );

    -- 10. ORDER_ITEMS (Immutable price & option JSON snapshots)
    CREATE TABLE IF NOT EXISTS order_items (
      id TEXT PRIMARY KEY,
      order_id TEXT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
      menu_item_id TEXT,
      item_name_snapshot TEXT NOT NULL,
      unit_price_snapshot INTEGER NOT NULL,
      selected_options_snapshot TEXT NOT NULL DEFAULT '[]',
      quantity INTEGER NOT NULL CHECK (quantity > 0),
      special_note TEXT NOT NULL DEFAULT ''
    );

    -- 11. REVIEWS (1..5 stars + comment on COMPLETED order)
    CREATE TABLE IF NOT EXISTS reviews (
      id TEXT PRIMARY KEY,
      order_id TEXT NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
      customer_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
      comment TEXT NOT NULL DEFAULT '',
      created_at TEXT NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_menu_items_cat ON menu_items(category_id);
    CREATE INDEX IF NOT EXISTS idx_option_groups_dish ON option_groups(menu_item_id);
    CREATE INDEX IF NOT EXISTS idx_options_group ON options(group_id);
    CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders(customer_id, created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status, created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);
  `);
}

function seedIfEmpty(db) {
  const existingStore = db.prepare("SELECT id FROM store_settings WHERE id = 1").get();
  if (existingStore) return;

  const nowIso = new Date().toISOString();
  const plusDaysIso = (days) => new Date(Date.now() + days * 86400 * 1000).toISOString();
  const minusMinsIso = (mins) => new Date(Date.now() - mins * 60 * 1000).toISOString();
  const passwordHash = bcrypt.hashSync("123456", 10);

  runInTransaction(db, () => {
    // Demo users
    const insertUser = db.prepare(`
      INSERT INTO users (id, email, password_hash, full_name, phone, role, wallet_balance, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `);
    insertUser.run(
      "cust-demo-001",
      "khach@coba.vn",
      passwordHash,
      "Nguyễn Minh Anh",
      "0908123456",
      "CUSTOMER",
      500000,
      nowIso
    );
    insertUser.run(
      "merch-demo-001",
      "quan@coba.vn",
      passwordHash,
      "Cô Ba Chủ Quán",
      "0901999888",
      "MERCHANT",
      0,
      nowIso
    );

    // Store settings
    db.prepare(`
      INSERT INTO store_settings (id, name, address, lat, lng, manual_status, busy_until)
      VALUES (1, ?, ?, ?, ?, 'OPEN', NULL)
    `).run(
      "Bún Riêu & Bún Chả Cá Cô Ba",
      "227 Nguyễn Văn Cừ, Phường 4, Quận 5, TP.HCM",
      10.762622,
      106.682172
    );

    // Weekly schedules (1 = Monday .. 7 = Sunday)
    const insertSched = db.prepare(`
      INSERT INTO store_schedules (id, type, day_of_week, specific_date, open_time, close_time, is_closed)
      VALUES (?, 'WEEKLY', ?, NULL, ?, ?, 0)
    `);
    for (let dow = 1; dow <= 7; dow++) {
      insertSched.run(`sched-week-${dow}`, dow, dow === 7 ? "06:30" : "06:00", "23:30");
    }

    // Categories
    const insertCat = db.prepare(
      "INSERT INTO categories (id, name, sort_order, is_active) VALUES (?, ?, ?, 1)"
    );
    insertCat.run("cat-main", "Món chính", 1);
    insertCat.run("cat-side", "Món thêm", 2);
    insertCat.run("cat-drink", "Thức uống", 3);

    // Menu items
    const insertDish = db.prepare(`
      INSERT INTO menu_items (id, category_id, name, description, base_price, image_url, is_available)
      VALUES (?, ?, ?, ?, ?, ?, 1)
    `);
    insertDish.run(
      "dish-bun-rieu",
      "cat-main",
      "Bún Riêu Cua Đặc Biệt",
      "Riêu cua đồng nguyên chất, đậu hũ chiên giòn, chả lụa, huyết mềm, rau sống tươi.",
      55000,
      "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=800"
    );
    insertDish.run(
      "dish-bun-cha-ca",
      "cat-main",
      "Bún Chả Cá Nha Trang",
      "Chả cá thu chiên & hấp dai ngọt chuẩn vị Nha Trang, nước dùng thanh trong hầm xương cá.",
      50000,
      "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=800"
    );
    insertDish.run(
      "dish-bun-gio",
      "cat-main",
      "Bún Riêu Giò Heo Chả Cá",
      "Tô đầy đủ riêu cua đồng béo thơm, khoanh giò heo rút xương mềm rục và chả cá thu.",
      65000,
      "https://images.unsplash.com/photo-1555126634-323283e090fa?w=800"
    );
    insertDish.run(
      "dish-cha-ca-them",
      "cat-side",
      "Chén Chả Cá Thu Chiên Nóng",
      "100g chả cá thu quết tay chiên phồng chấm mắm ớt tỏi.",
      25000,
      "https://images.unsplash.com/photo-1541696432-82c6da8ce7bf?w=800"
    );
    insertDish.run(
      "dish-tra-tac",
      "cat-drink",
      "Trà Tắc Xí Muội Nhà Làm",
      "Giải nhiệt thanh mát với tắc tươi và xí muội mặn ngọt.",
      18000,
      "https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=800"
    );
    insertDish.run(
      "dish-sua-dau-nanh",
      "cat-drink",
      "Sữa Đậu Nành Lá Dứa",
      "Nấu mới mỗi sáng từ đậu nành quê thơm mùi lá dứa.",
      15000,
      "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=800"
    );

    // Option groups & options
    const insertGrp = db.prepare(`
      INSERT INTO option_groups (id, menu_item_id, name, selection_type, is_required)
      VALUES (?, ?, ?, ?, ?)
    `);
    insertGrp.run("grp-br-size", "dish-bun-rieu", "Kích cỡ tô", "SINGLE", 1);
    insertGrp.run("grp-br-top", "dish-bun-rieu", "Món ăn kèm", "MULTI", 0);
    insertGrp.run("grp-bcc-size", "dish-bun-cha-ca", "Kích cỡ tô", "SINGLE", 1);
    insertGrp.run("grp-bcc-top", "dish-bun-cha-ca", "Topping thêm", "MULTI", 0);
    insertGrp.run("grp-bg-size", "dish-bun-gio", "Kích cỡ tô", "SINGLE", 1);
    insertGrp.run("grp-sdn-temp", "dish-sua-dau-nanh", "Cách dùng", "SINGLE", 1);

    const insertOpt = db.prepare(`
      INSERT INTO options (id, group_id, name, extra_price, is_available)
      VALUES (?, ?, ?, ?, 1)
    `);
    insertOpt.run("opt-br-reg", "grp-br-size", "Tô thường", 0);
    insertOpt.run("opt-br-lg", "grp-br-size", "Tô lớn đặc biệt", 10000);
    insertOpt.run("opt-br-kid", "grp-br-size", "Tô trẻ em", -5000);
    insertOpt.run("opt-br-cha", "grp-br-top", "Thêm chả cá", 15000);
    insertOpt.run("opt-br-gio", "grp-br-top", "Thêm giò heo", 20000);
    insertOpt.run("opt-br-mamtom", "grp-br-top", "Mắm tôm để riêng", 0);

    insertOpt.run("opt-bcc-reg", "grp-bcc-size", "Tô thường", 0);
    insertOpt.run("opt-bcc-lg", "grp-bcc-size", "Tô lớn nhiều chả", 12000);
    insertOpt.run("opt-bcc-sua", "grp-bcc-top", "Thêm sứa giòn", 15000);
    insertOpt.run("opt-bcc-rau", "grp-bcc-top", "Rau sống thêm", 5000);

    insertOpt.run("opt-bg-reg", "grp-bg-size", "Tô tiêu chuẩn", 0);
    insertOpt.run("opt-bg-xl", "grp-bg-size", "Tô đại 2 khoanh giò", 18000);

    insertOpt.run("opt-sdn-cold", "grp-sdn-temp", "Uống đá", 0);
    insertOpt.run("opt-sdn-hot", "grp-sdn-temp", "Uống nóng", 0);

    // Vouchers
    const insertVoucher = db.prepare(`
      INSERT INTO vouchers (id, code, discount_type, discount_value, min_order_value, usage_limit, used_count, end_at, is_active)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
    `);
    insertVoucher.run("vch-1", "GIAM20K", "FIXED", 20000, 100000, 50, 4, plusDaysIso(60));
    insertVoucher.run("vch-2", "BUNRIEU15", "PERCENT", 15, 50000, 100, 12, plusDaysIso(30));
    insertVoucher.run("vch-3", "SIEUVIP100K", "FIXED", 100000, 40000, 10, 1, plusDaysIso(30));

    // Seed 1 completed historical order + review for Reorder demo
    db.prepare(`
      INSERT INTO orders (
        id, customer_id, voucher_code, status, delivery_address, delivery_lat, delivery_lng,
        distance_km, subtotal_amount, discount_amount, shipping_fee, total_amount,
        payment_method, payment_status, driver_name, driver_phone, driver_plate,
        picked_up_at, created_at
      ) VALUES (?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?, ?, ?, ?, 'WALLET', 'PAID', ?, ?, ?, ?, ?)
    `).run(
      "ord-completed-seed",
      "cust-demo-001",
      "BUNRIEU15",
      "135 Nam Kỳ Khởi Nghĩa, Quận 1, TP.HCM",
      10.776889,
      106.695278,
      2.6,
      88000,
      13200,
      20000,
      94800,
      "Trần Quốc Bảo",
      "0903555777",
      "59P2-456.78",
      minusMinsIso(2880),
      minusMinsIso(2900)
    );

    const insertOrderItem = db.prepare(`
      INSERT INTO order_items (
        id, order_id, menu_item_id, item_name_snapshot, unit_price_snapshot,
        selected_options_snapshot, quantity, special_note
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `);
    insertOrderItem.run(
      "oi-seed-1",
      "ord-completed-seed",
      "dish-bun-rieu",
      "Bún Riêu Cua Đặc Biệt",
      70000,
      JSON.stringify([
        { option_id: "opt-br-reg", group_name: "Kích cỡ tô", option_name: "Tô thường", extra_price: 0 },
        { option_id: "opt-br-cha", group_name: "Món ăn kèm", option_name: "Thêm chả cá", extra_price: 15000 },
        { option_id: "opt-br-mamtom", group_name: "Món ăn kèm", option_name: "Mắm tôm để riêng", extra_price: 0 }
      ]),
      1,
      "Ít cay, không hành lá"
    );
    insertOrderItem.run(
      "oi-seed-2",
      "ord-completed-seed",
      "dish-tra-tac",
      "Trà Tắc Xí Muội Nhà Làm",
      18000,
      "[]",
      1,
      "Ít đá"
    );

    db.prepare(`
      INSERT INTO reviews (id, order_id, customer_id, rating, comment, created_at)
      VALUES (?, ?, ?, 5, ?, ?)
    `).run(
      crypto.randomUUID(),
      "ord-completed-seed",
      "cust-demo-001",
      "Nước lèo đậm đà, mắm tôm để riêng sạch sẽ, giao nhanh còn nóng hổi!",
      minusMinsIso(2850)
    );
  });
}

module.exports = {
  createDatabase,
  getDb,
  runInTransaction
};
