const crypto = require("node:crypto");

/**
 * Signs HMAC-SHA256 requests to Lalamove v3 Sandbox API (/v3/quotations, /v3/orders).
 * Automatically falls back to MOCK_SANDBOX when LALAMOVE_API_KEY is not configured.
 */
async function dispatchToLalamove({ store, order }) {
  const host = process.env.LALAMOVE_HOST || "https://rest.sandbox.lalamove.com";
  const apiKey = process.env.LALAMOVE_API_KEY || "";
  const apiSecret = process.env.LALAMOVE_API_SECRET || "";

  if (!apiKey || !apiSecret) {
    return {
      mode: "MOCK_SANDBOX",
      externalOrderId: `mock-lala-${order.id.slice(0, 8)}`,
      message: "Đang chạy chế độ Lalamove Mock Sandbox (chưa cấu hình LALAMOVE_API_KEY)."
    };
  }

  const callSigned = async (method, apiPath, bodyObj) => {
    const time = Date.now().toString();
    const body = bodyObj ? JSON.stringify(bodyObj) : "";
    const rawSig = `${time}\r\n${method}\r\n${apiPath}\r\n\r\n${body}`;
    const signature = crypto.createHmac("sha256", apiSecret).update(rawSig).digest("hex");

    const response = await fetch(`${host}${apiPath}`, {
      method,
      headers: {
        "Content-Type": "application/json",
        Authorization: `hmac ${apiKey}:${time}:${signature}`,
        Market: "VN",
        "Request-ID": crypto.randomUUID()
      },
      body: method === "GET" ? undefined : body
    });
    return response.json();
  };

  const quoteRes = await callSigned("POST", "/v3/quotations", {
    data: {
      serviceType: "MOTORCYCLE",
      language: "vi_VN",
      stops: [
        {
          coordinates: { lat: String(store.lat), lng: String(store.lng) },
          address: store.address
        },
        {
          coordinates: { lat: String(order.delivery_lat), lng: String(order.delivery_lng) },
          address: order.delivery_address
        }
      ]
    }
  });

  const quotationId = quoteRes?.data?.quotationId;
  const stops = quoteRes?.data?.stops || [];
  const codAmount = order.payment_method === "COD" ? order.total_amount : 0;

  const orderRes = await callSigned("POST", "/v3/orders", {
    data: {
      quotationId,
      sender: {
        stopId: stops[0]?.stopId,
        name: store.name,
        phone: "+84901999888"
      },
      recipients: [
        {
          stopId: stops[1]?.stopId,
          name: order.customerName || "Khách đặt món",
          phone: order.customerPhone || "+84908123456",
          remarks: `Đơn #${order.id.slice(0, 6)} • Thu hộ COD: ${codAmount}đ`
        }
      ],
      metadata: { internalOrderId: order.id }
    }
  });

  return {
    mode: "LALAMOVE_LIVE_SANDBOX",
    data: orderRes?.data || orderRes
  };
}

module.exports = { dispatchToLalamove };
