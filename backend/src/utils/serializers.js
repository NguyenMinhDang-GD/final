/**
 * Maps SQLite rows (0/1 integers & JSON text) to exact JSON contracts
 * consumed by the Android Kotlin (@Serializable) data models.
 */
function formatUser(row) {
  if (!row) return null;
  return {
    id: row.id,
    email: row.email,
    full_name: row.full_name,
    phone: row.phone || "",
    role: row.role,
    wallet_balance: Number(row.wallet_balance || 0)
  };
}

function formatStoreSettings(row) {
  return {
    id: Number(row.id),
    name: row.name,
    address: row.address,
    lat: Number(row.lat),
    lng: Number(row.lng),
    manual_status: row.manual_status,
    busy_until: row.busy_until || null
  };
}

function formatSchedule(row) {
  return {
    id: row.id,
    type: row.type,
    day_of_week: row.day_of_week !== null ? Number(row.day_of_week) : null,
    specific_date: row.specific_date || null,
    open_time: row.open_time,
    close_time: row.close_time,
    is_closed: Boolean(row.is_closed)
  };
}

function assembleCatalog(db) {
  const categories = db
    .prepare("SELECT * FROM categories ORDER BY sort_order ASC")
    .all()
    .map((c) => ({
      id: c.id,
      name: c.name,
      sort_order: Number(c.sort_order),
      is_active: Boolean(c.is_active)
    }));

  const rawDishes = db.prepare("SELECT * FROM menu_items").all();
  const rawGroups = db.prepare("SELECT * FROM option_groups").all();
  const rawOptions = db.prepare("SELECT * FROM options").all();

  const optionsByGroup = new Map();
  for (const opt of rawOptions) {
    const formatted = {
      id: opt.id,
      group_id: opt.group_id,
      name: opt.name,
      extra_price: Number(opt.extra_price),
      is_available: Boolean(opt.is_available)
    };
    if (!optionsByGroup.has(opt.group_id)) optionsByGroup.set(opt.group_id, []);
    optionsByGroup.get(opt.group_id).push(formatted);
  }

  const groupsByDish = new Map();
  for (const grp of rawGroups) {
    const formatted = {
      id: grp.id,
      menu_item_id: grp.menu_item_id,
      name: grp.name,
      selection_type: grp.selection_type,
      is_required: Boolean(grp.is_required),
      options: optionsByGroup.get(grp.id) || []
    };
    if (!groupsByDish.has(grp.menu_item_id)) groupsByDish.set(grp.menu_item_id, []);
    groupsByDish.get(grp.menu_item_id).push(formatted);
  }

  const menuItems = rawDishes.map((d) => ({
    id: d.id,
    category_id: d.category_id,
    name: d.name,
    description: d.description || "",
    base_price: Number(d.base_price),
    image_url: d.image_url || "",
    is_available: Boolean(d.is_available),
    optionGroups: groupsByDish.get(d.id) || []
  }));

  const vouchers = db
    .prepare("SELECT * FROM vouchers ORDER BY code ASC")
    .all()
    .map((v) => ({
      id: v.id,
      code: v.code,
      discount_type: v.discount_type,
      discount_value: Number(v.discount_value),
      min_order_value: Number(v.min_order_value),
      usage_limit: Number(v.usage_limit),
      used_count: Number(v.used_count),
      end_at: v.end_at,
      is_active: Boolean(v.is_active)
    }));

  return { categories, menuItems, vouchers };
}

function assembleOrders(db, filterCustomerId = null) {
  const rawOrders = filterCustomerId
    ? db
        .prepare("SELECT * FROM orders WHERE customer_id = ? ORDER BY created_at DESC")
        .all(filterCustomerId)
    : db.prepare("SELECT * FROM orders ORDER BY created_at DESC").all();

  if (rawOrders.length === 0) return [];

  const usersMap = new Map(
    db
      .prepare("SELECT id, full_name, phone FROM users")
      .all()
      .map((u) => [u.id, u])
  );
  const allItems = db.prepare("SELECT * FROM order_items").all();
  const allReviews = db.prepare("SELECT * FROM reviews").all();

  const itemsByOrder = new Map();
  for (const it of allItems) {
    let parsedOptions = [];
    try {
      parsedOptions = JSON.parse(it.selected_options_snapshot || "[]");
    } catch (_) {
      parsedOptions = [];
    }
    const formatted = {
      id: it.id,
      order_id: it.order_id,
      menu_item_id: it.menu_item_id || null,
      item_name_snapshot: it.item_name_snapshot,
      unit_price_snapshot: Number(it.unit_price_snapshot),
      selected_options_snapshot: parsedOptions,
      quantity: Number(it.quantity),
      special_note: it.special_note || ""
    };
    if (!itemsByOrder.has(it.order_id)) itemsByOrder.set(it.order_id, []);
    itemsByOrder.get(it.order_id).push(formatted);
  }

  const reviewByOrder = new Map();
  for (const rev of allReviews) {
    const cust = usersMap.get(rev.customer_id);
    reviewByOrder.set(rev.order_id, {
      id: rev.id,
      order_id: rev.order_id,
      customer_id: rev.customer_id,
      customerName: cust?.full_name || "Khách hàng",
      rating: Number(rev.rating),
      comment: rev.comment || "",
      created_at: rev.created_at
    });
  }

  return rawOrders.map((o) => {
    const cust = usersMap.get(o.customer_id);
    return {
      id: o.id,
      customer_id: o.customer_id,
      customerName: cust?.full_name || "Khách hàng",
      customerPhone: cust?.phone || "",
      voucher_code: o.voucher_code || null,
      status: o.status,
      delivery_address: o.delivery_address,
      delivery_lat: Number(o.delivery_lat),
      delivery_lng: Number(o.delivery_lng),
      distance_km: Number(o.distance_km),
      subtotal_amount: Number(o.subtotal_amount),
      discount_amount: Number(o.discount_amount),
      shipping_fee: Number(o.shipping_fee),
      total_amount: Number(o.total_amount),
      payment_method: o.payment_method,
      payment_status: o.payment_status,
      reject_reason: o.reject_reason || null,
      driver_name: o.driver_name || null,
      driver_phone: o.driver_phone || null,
      driver_plate: o.driver_plate || null,
      picked_up_at: o.picked_up_at || null,
      created_at: o.created_at,
      items: itemsByOrder.get(o.id) || [],
      review: reviewByOrder.get(o.id) || null
    };
  });
}

function getOrderById(db, orderId) {
  return assembleOrders(db, null).find((o) => o.id === orderId) || null;
}

module.exports = {
  formatUser,
  formatStoreSettings,
  formatSchedule,
  assembleCatalog,
  assembleOrders,
  getOrderById
};
