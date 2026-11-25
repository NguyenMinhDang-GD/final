const jwt = require("jsonwebtoken");

const JWT_SECRET = () => process.env.JWT_SECRET || "coba-d2c-fnb-secret-key-2026";

function signUserToken(user) {
  return jwt.sign(
    {
      sub: user.id,
      email: user.email,
      role: user.role
    },
    JWT_SECRET(),
    { expiresIn: "14d" }
  );
}

function authenticateJwt(req, res, next) {
  const authHeader = req.headers.authorization || "";
  const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";
  if (!token) {
    return res.status(401).json({ error: "Thiếu mã xác thực JWT (Authorization Bearer)." });
  }
  try {
    const decoded = jwt.verify(token, JWT_SECRET());
    req.auth = {
      userId: decoded.sub,
      email: decoded.email,
      role: decoded.role
    };
    return next();
  } catch (err) {
    return res.status(401).json({ error: "Phiên đăng nhập không hợp lệ hoặc đã hết hạn." });
  }
}

function requireMerchant(req, res, next) {
  if (!req.auth || req.auth.role !== "MERCHANT") {
    return res.status(403).json({ error: "Chỉ tài khoản Chủ quán (MERCHANT) mới được phép thực hiện thao tác này." });
  }
  return next();
}

module.exports = {
  JWT_SECRET,
  signUserToken,
  authenticateJwt,
  requireMerchant
};
