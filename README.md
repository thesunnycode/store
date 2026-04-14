# Store — E-Commerce REST API

A production-ready e-commerce backend built with **Spring Boot 3**, **Spring Security**, **JWT**, **MySQL**, and **Stripe**.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Security | Spring Security + JWT (jjwt 0.12) |
| Database | MySQL + Spring Data JPA (Hibernate) |
| Payments | Stripe Java SDK |
| Build Tool | Maven |
| Deployment | Railway |

---

## Project Structure

```
src/main/java/com/ecommerce/
├── config/          # SecurityConfig, StripeConfig
├── controller/      # AuthController, ProductController, CartController, OrderController, PaymentController
├── dto/
│   ├── request/     # RegisterRequest, LoginRequest, ProductRequest, CartItemRequest, CheckoutRequest
│   └── response/    # AuthResponse, ProductResponse, CartResponse, OrderResponse, ApiResponse
├── exception/       # GlobalExceptionHandler, ResourceNotFoundException, BadRequestException
├── model/           # User, Product, Cart, CartItem, Order, OrderItem
│   └── enums/       # Role, OrderStatus
├── repository/      # UserRepository, ProductRepository, CartRepository, OrderRepository...
├── security/        # JwtUtil, JwtAuthFilter, UserDetailsServiceImpl
└── service/         # AuthService, ProductService, CartService, OrderService, PaymentService
```

---

## Setup — Local Development

### Prerequisites
- Java 17+
- Maven 3.8+
- MySQL 8+
- Stripe account (free)

### Steps

**1. Clone the repo**
```bash
git clone https://github.com/yourusername/ecommerce-api.git
cd ecommerce-api
```

**2. Create the MySQL database**
```sql
CREATE DATABASE ecommerce_db;
```

**3. Configure application-dev.properties**
```properties
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD
app.jwt.secret=YOUR_BASE64_SECRET   # openssl rand -base64 32
stripe.secret-key=sk_test_...
stripe.webhook-secret=whsec_...
```

**4. Run the application**
```bash
mvn spring-boot:run
```
App starts at: `http://localhost:8080`

---

## API Reference

### Authentication

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| POST | `/api/auth/register` | None | Register new customer |
| POST | `/api/auth/login` | None | Login, get JWT |

**Register**
```json
POST /api/auth/register
{
  "name": "Sunny Kumar",
  "email": "sunny@example.com",
  "password": "password123"
}
```

**Login**
```json
POST /api/auth/login
{
  "email": "sunny@example.com",
  "password": "password123"
}
```

**Response (both)**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGci...",
    "type": "Bearer",
    "userId": 1,
    "name": "Sunny Kumar",
    "email": "sunny@example.com",
    "role": "CUSTOMER"
  }
}
```
> Use the token in all subsequent requests: `Authorization: Bearer <token>`

---

### Products

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| GET | `/api/products` | None | List all products (paginated) |
| GET | `/api/products/{id}` | None | Get product by ID |
| GET | `/api/products/search?query=laptop` | None | Search products |
| GET | `/api/products/category?name=Electronics` | None | Filter by category |
| POST | `/api/products` | ADMIN | Create product |
| PUT | `/api/products/{id}` | ADMIN | Update product |
| DELETE | `/api/products/{id}` | ADMIN | Soft-delete product |

**Create Product (Admin)**
```json
POST /api/products
Authorization: Bearer <admin_token>
{
  "name": "MacBook Pro 14",
  "description": "Apple M3 chip, 16GB RAM",
  "price": 199999.00,
  "stockQuantity": 50,
  "category": "Electronics",
  "imageUrl": "https://example.com/macbook.jpg"
}
```

---

### Cart

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| GET | `/api/cart` | Customer | View cart |
| POST | `/api/cart/items` | Customer | Add item to cart |
| PUT | `/api/cart/items/{id}?quantity=2` | Customer | Update quantity |
| DELETE | `/api/cart/items/{id}` | Customer | Remove item |

**Add to Cart**
```json
POST /api/cart/items
Authorization: Bearer <customer_token>
{
  "productId": 1,
  "quantity": 2
}
```

---

### Orders

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| POST | `/api/orders/checkout` | Customer | Place order from cart |
| GET | `/api/orders/my` | Customer | My order history |
| GET | `/api/orders/my/{id}` | Customer | Single order details |
| GET | `/api/orders/all` | ADMIN | All orders |
| PUT | `/api/orders/{id}/status?status=SHIPPED` | ADMIN | Update order status |

**Checkout**
```json
POST /api/orders/checkout
Authorization: Bearer <customer_token>
{
  "shippingAddress": "123 MG Road, Bengaluru, Karnataka 560001"
}
```

---

### Payments

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| POST | `/api/payments/create-intent` | Customer | Create Stripe PaymentIntent |
| POST | `/api/payments/webhook` | None (Stripe) | Stripe webhook handler |

**Create Payment Intent**
```json
POST /api/payments/create-intent
Authorization: Bearer <customer_token>
{
  "orderId": 1,
  "currency": "inr"
}
```

---

## Deployment on Railway

1. Push code to GitHub
2. Go to [railway.app](https://railway.app) → New Project → Deploy from GitHub
3. Add a MySQL plugin to your project
4. Set environment variables in the Variables tab:
   ```
   SPRING_PROFILES_ACTIVE=prod
   JWT_SECRET=<base64 string>
   STRIPE_SECRET_KEY=sk_live_...
   STRIPE_WEBHOOK_SECRET=whsec_...
   ```
5. Railway auto-injects `MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`, and `PORT`
6. Deploy → Railway builds the JAR and starts the app

Health check URL: `https://your-app.railway.app/actuator/health`

---

## Key Design Decisions (for interviews)

### Why JWT over Sessions?
- **Stateless**: server stores nothing — scales horizontally without sticky sessions
- **Self-contained**: user identity and role are inside the token — no DB lookup per request
- **Mobile-friendly**: works easily with mobile apps that don't use cookies

### Why BCrypt for passwords?
- Intentionally slow (cost factor = 10) — brute-force attacks take years
- Salted automatically — same password produces different hashes

### Why soft-delete for products?
- Order history references products by ID — hard delete would break historical data
- Easier to recover accidentally deleted items

### Why BigDecimal for prices?
- `double`/`float` have rounding errors in binary math
- `0.1 + 0.2 = 0.30000000000000004` in floating point
- `BigDecimal` is exact — critical for financial calculations

### Why snapshot price in OrderItem?
- Product prices can change — order history must show what the customer actually paid
- OrderItem stores `priceAtPurchase` separately from the live `Product.price`

### Why @Transactional on checkout?
- Multiple operations: create order → deduct stock → clear cart
- If any step fails, the entire transaction rolls back
- Without it: order could be created but stock never deducted

### Stripe Payment Flow
1. Backend creates PaymentIntent → returns `clientSecret`
2. Frontend uses Stripe.js + `clientSecret` to collect card details
3. Card details **never touch our server** → PCI compliant
4. Stripe sends webhook → we confirm the order

---

## Interview Questions You'll Likely Get

**Q: How does JWT authentication work in this project?**
> "On login, we verify credentials with AuthenticationManager. If valid, JwtUtil generates a signed token containing the user's email and role. On every subsequent request, JwtAuthFilter reads the Authorization header, extracts and validates the token, then sets the user in Spring Security's SecurityContext. The controller gets the User via @AuthenticationPrincipal."

**Q: What is the difference between Authentication and Authorization?**
> "Authentication = verifying who you are (login). Authorization = verifying what you're allowed to do (role checks). JWT handles authentication. @PreAuthorize and SecurityConfig rules handle authorization."

**Q: What happens if two users try to buy the last item at the same time?**
> "This is the concurrent stock problem. Currently we validate stock before deducting it, but two simultaneous requests could both pass validation before either deducts. Production solution: database-level locking with @Lock(PESSIMISTIC_WRITE) on the findById query, or using optimistic locking with @Version on the Product entity."

**Q: Why is the webhook endpoint public?**
> "Stripe calls our webhook from their servers — they don't have a user JWT. But we still verify authenticity using HMAC signature verification (Webhook.constructEvent). If the signature doesn't match, we reject it."

**Q: What is Spring Security's filter chain?**
> "Every HTTP request passes through an ordered list of filters before reaching the controller. Our JwtAuthFilter runs before UsernamePasswordAuthenticationFilter. It reads the JWT, validates it, and sets authentication in the SecurityContext so subsequent filters and the controller know who made the request."
