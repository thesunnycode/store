# Complete Interview Guide — Store (E-Commerce REST API)

> Read this top to bottom at least twice before your interview.
> Every answer is written so you can say it naturally out loud.

---

## PART 1 — "Tell Me About Your Project" (Opening)

### Q: Tell me about this project you built.

**Say this:**

> "I built a production-ready REST API backend for an e-commerce platform called Store. It's a complete backend system — users can register, log in, browse products, add items to a cart, place orders, and pay with a card through Stripe.
>
> I used Java with Spring Boot 3 as the main framework, Spring Security with JWT for authentication, MySQL as the database with JPA for ORM, and deployed it on Railway.
>
> The project has two roles — Admin and Customer. Admins can manage the product catalog, view all orders, and update order statuses. Customers can do everything related to shopping — cart, checkout, order history.
>
> I focused on making it production-quality — proper error handling, input validation, paginated responses, and environment-specific config using Spring profiles for dev and production."

---

## PART 2 — Authentication & Security (Most Asked)

### Q: How does authentication work in your project?

> "When a user logs in, Spring Security's AuthenticationManager verifies their email and password against the database. The password is stored as a BCrypt hash — so it compares the hash, not the plain text. If credentials are correct, I generate a JWT using the jjwt library.
>
> On every subsequent request, a filter called JwtAuthFilter reads the Authorization header, extracts the token, validates it, and sets the user in Spring Security's SecurityContext. After that, the controller just uses @AuthenticationPrincipal to get the logged-in user object directly."

---

### Q: What is a JWT? What's inside it?

> "JWT stands for JSON Web Token. It has three parts separated by dots: Header, Payload, and Signature.
>
> The Header says which algorithm was used — in my project it's HS256, which is HMAC with SHA-256.
>
> The Payload contains claims — pieces of data. I store the user's email as the subject, their role like ROLE_ADMIN or ROLE_CUSTOMER, and the expiration time. This means I don't need to hit the database on every request — everything I need is inside the token.
>
> The Signature is an HMAC-SHA256 hash of the header and payload using a secret key stored on my server. If anyone tampers with the payload, the signature won't match and the token is rejected."

---

### Q: Why JWT instead of sessions?

> "With sessions, the server has to store session data and look it up on every request — this doesn't scale well horizontally because if you have multiple server instances, they all need to share session storage.
>
> JWT is stateless — the server stores nothing. All the user's info is inside the token. Any server instance can validate it just by knowing the secret key. This scales perfectly and works well with mobile clients that don't use cookies."

---

### Q: What is BCrypt? Why not MD5 or SHA-256?

> "BCrypt is a password hashing algorithm designed specifically for passwords. The key property is that it's intentionally slow — it has a cost factor, set to 10 by default in Spring, which means it does 2^10 = 1024 rounds of hashing.
>
> MD5 and SHA-256 are fast — great for data integrity but terrible for passwords because an attacker can hash millions of guesses per second.
>
> BCrypt also automatically generates a random salt for each password, so even if two users have the same password, their hashes are different. This prevents rainbow table attacks."

---

### Q: What is Spring Security's filter chain?

> "Every incoming HTTP request passes through an ordered list of filters before reaching the controller. It's like a pipeline.
>
> In my project, I added a custom JwtAuthFilter before Spring's built-in UsernamePasswordAuthenticationFilter. So when a request comes in, my filter runs first — it reads the JWT from the Authorization header, validates it, and if it's valid, it sets the authentication object in the SecurityContext. Then the rest of the chain, including Spring's own filters, sees an already-authenticated request."

---

### Q: What is the SecurityContext?

> "The SecurityContext is a thread-local storage that Spring Security uses to hold the current user's authentication information for the duration of that request. Once JwtAuthFilter sets the Authentication object in it, any part of the application — services, controllers — can call SecurityContextHolder.getContext().getAuthentication() to know who made this request. My controllers use @AuthenticationPrincipal which is a shorthand for doing exactly that."

---

### Q: What is @PreAuthorize? How does role-based access work?

> "In my SecurityConfig, I enabled method-level security with @EnableMethodSecurity. Then on specific controller methods, I added @PreAuthorize("hasRole('ADMIN')"). Before the method runs, Spring Security intercepts the call, checks the roles in the SecurityContext, and if the user doesn't have ADMIN role, it throws AccessDeniedException which my GlobalExceptionHandler converts to a 403 Forbidden response.
>
> I also have endpoint-level rules in SecurityConfig for broader patterns — like all GET requests to /api/products are public, but POST/PUT/DELETE require ADMIN."

---

## PART 3 — Database & JPA

### Q: Explain your database design / entities.

> "I have six main entities:
>
> **User** — stores name, email, BCrypt password hash, and role. Email is unique and used as the login identifier.
>
> **Product** — stores name, description, price as BigDecimal, stock quantity, category, image URL, and an 'active' boolean for soft delete.
>
> **Cart and CartItem** — each user has exactly one persistent Cart created at registration. CartItem links a Cart to a Product with a quantity. There's a unique constraint on cart_id + product_id so the same product can't appear twice — we update quantity instead.
>
> **Order and OrderItem** — when checkout happens, an Order is created with a status. OrderItem stores the product name and price AS A SNAPSHOT — the price at the time of purchase, not the current price. This is important because prices can change."

---

### Q: What is JPA? What is Hibernate?

> "JPA stands for Java Persistence API — it's a specification, a set of interfaces and annotations, that defines how Java objects map to database tables. @Entity, @Table, @Column, @OneToMany — these are all JPA annotations.
>
> Hibernate is the most popular JPA implementation. It's the engine that actually generates and executes SQL. When I call userRepository.save(user), JPA defines what 'save' means, and Hibernate writes the actual INSERT SQL and runs it.
>
> Spring Data JPA sits on top of Hibernate and gives me repositories — interfaces where I just define method names like findByEmail and it generates the SQL automatically."

---

### Q: What is CascadeType.ALL and orphanRemoval?

> "CascadeType.ALL means that any JPA operation on the parent — save, delete, refresh — is automatically cascaded to the children. So when I save a Cart, its CartItems are also saved automatically.
>
> orphanRemoval = true means if a CartItem is removed from the cart's list in Java — cart.getCartItems().remove(item) — Hibernate automatically deletes that row from the database. Without it, the item would just be disconnected from the cart but still exist in the DB."

---

### Q: What is FetchType.LAZY vs EAGER?

> "EAGER means when you load an entity, all its related entities are loaded immediately in the same query. LAZY means the related entity is only loaded from the database when you actually access it in code.
>
> I use LAZY on most relationships. For example, @ManyToOne(fetch = FetchType.LAZY) on CartItem's cart field means when I load a CartItem, it doesn't automatically load the full Cart object. I only pay the database cost if I actually call item.getCart(). LAZY is better for performance because you avoid N+1 query problems and unnecessary data loading."

---

### Q: What is @Transactional? Why did you use it on checkout?

> "Checkout involves multiple database operations — creating an order, creating order items, deducting stock for each product, and clearing the cart. @Transactional wraps all of these in a single database transaction.
>
> A transaction is all-or-nothing — ACID. If any step fails — say deducting stock throws an exception — the entire transaction rolls back. The order is not created, the stock is not deducted, the cart is not cleared. The database stays in a consistent state.
>
> Without @Transactional, you could end up in a nightmare state — order created, but stock never deducted, or cart cleared but order never committed."

---

### Q: What is soft delete? Why did you use it for products?

> "Instead of running a DELETE SQL statement and removing the row permanently, soft delete sets a boolean flag — in my case product.active = false — and keeps the row in the database.
>
> I use it for products because orders reference products by ID. If I hard-deleted a product, old orders that reference that product ID would be broken — foreign key constraints, or worse, order history showing null product names.
>
> With soft delete, the product still exists in the database and order history is intact. The product just doesn't show up in the active catalog because all queries filter by active = true."

---

### Q: What is pagination? Why use it?

> "Pagination means returning a subset of results — say 10 products at a time — instead of loading everything at once. In a real e-commerce store there could be thousands of products. Fetching all of them at once would use massive memory, take too long, and crash the browser.
>
> Spring Data's Pageable interface handles this. I pass page number and page size — like page=0, size=10 — and Spring generates a SQL query with LIMIT and OFFSET. The response includes the data plus metadata like total pages, total elements, and current page number."

---

### Q: Why BigDecimal for price instead of double?

> "Floating point numbers like double use binary representation internally, which cannot exactly represent many decimal fractions. 0.1 + 0.2 equals 0.30000000000000004 in floating point. For displaying a blog post, that's fine. For money — where you're calculating totals and processing payments — that's a serious bug.
>
> BigDecimal is arbitrary precision and exact. 0.1 + 0.2 = exactly 0.3. I also defined the column with precision = 10, scale = 2 which means up to 99,999,999.99 with exactly 2 decimal places."

---

## PART 4 — REST API Design

### Q: What is a REST API? What makes it RESTful?

> "REST stands for Representational State Transfer. It's an architectural style for designing APIs based on a few principles:
>
> First, resources are identified by URLs — /api/products is the products resource, /api/products/1 is a specific product.
>
> Second, HTTP methods indicate the operation — GET for reading, POST for creating, PUT for updating, DELETE for removing.
>
> Third, it's stateless — each request contains all the information needed to process it. The server doesn't remember previous requests. That's why we send the JWT in every request.
>
> Fourth, responses use standard HTTP status codes — 200 OK, 201 Created, 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 500 Internal Server Error."

---

### Q: What is the difference between 401 and 403?

> "401 Unauthorized means the request is missing valid authentication — either there's no token, or the token is expired or invalid. You need to log in first.
>
> 403 Forbidden means you are authenticated — we know who you are — but you don't have permission to do this. For example, a CUSTOMER trying to delete a product gets 403 because they're logged in but not an ADMIN."

---

### Q: Why do you use DTOs instead of returning entities directly?

> "Three reasons. Security — the User entity has the password hash. I never want to accidentally include that in an API response. DTOs let me precisely control what gets sent.
>
> Second, decoupling — if I change my database schema, I don't want to break the API contract. DTOs create a stable interface between the API and the database layer.
>
> Third, computed fields — ProductResponse has an inStock field which is stockQuantity > 0. That's not a database column, it's computed at response time. Easy to add in a DTO, awkward in an entity."

---

### Q: What is @PathVariable vs @RequestParam?

> "@PathVariable reads from the URL path itself. In /api/products/{id}, the {id} is a path variable. I'd write @PathVariable Long id in the method signature.
>
> @RequestParam reads from query parameters — the key=value pairs after the ? in the URL. In /api/products?page=0&size=10, both page and size are request params. I'd write @RequestParam(defaultValue='0') int page."

---

### Q: What is the purpose of your GlobalExceptionHandler?

> "Without it, if an exception is thrown anywhere in the app, Spring would return an HTML error page or an ugly default JSON with a stack trace. That's inconsistent and exposes internal details.
>
> My GlobalExceptionHandler is annotated with @RestControllerAdvice. It has @ExceptionHandler methods for each exception type — ResourceNotFoundException returns 404, BadRequestException returns 400, BadCredentialsException returns 401, and so on. Every error response uses my ApiResponse wrapper, so the client always gets consistent JSON with success: false and a readable message."

---

## PART 5 — Stripe & Payments

### Q: How does Stripe payment work in your project?

> "There are two sides — server and client. On the server, when the customer is ready to pay, they call my /api/payments/create-intent endpoint with their order ID. I create a Stripe PaymentIntent — this is a Stripe object that tracks the payment lifecycle. Stripe returns a clientSecret string.
>
> My server sends this clientSecret to the frontend. The frontend uses Stripe.js — Stripe's official JavaScript library — to collect the card number, CVV, and expiry. The card details go directly from the browser to Stripe's servers. They never touch my backend at all. This is called Stripe Elements.
>
> Once Stripe processes the payment, it sends a webhook POST request to my /api/payments/webhook endpoint. I verify the signature, check the event type is payment_intent.succeeded, look up the order by the PaymentIntent ID, and update the order status to CONFIRMED."

---

### Q: Why verify the webhook signature?

> "Because anyone on the internet can POST to my /api/payments/webhook URL. Without verification, a malicious actor could send a fake payment_intent.succeeded event, and my server would mark an order as CONFIRMED even though no real payment happened — free products.
>
> Stripe signs every webhook with a secret using HMAC-SHA256. I call Webhook.constructEvent() with the raw payload and the Stripe-Signature header. If the signature doesn't match my webhook secret, it throws SignatureVerificationException and I reject the request with a 400."

---

### Q: Why does the webhook endpoint not require JWT authentication?

> "Because it's called by Stripe's servers, not by a user's browser. Stripe doesn't log in to my API — they just POST to the URL. They can't provide a JWT. The authentication for this endpoint is the webhook signature verification — that's how I verify the caller is Stripe and not someone else."

---

### Q: What is PCI compliance? How does your project handle it?

> "PCI DSS is a security standard for handling card data. If card details pass through your server, you're in scope for PCI compliance which requires extensive security audits, penetration testing, and certifications — very expensive.
>
> By using Stripe Elements, card details go directly from the browser to Stripe's servers. My server never sees the card number, CVV, or expiry. The only thing that goes between the browser and my server is the clientSecret — a temporary ID with no card data. So my server is completely out of PCI scope."

---

## PART 6 — Spring Boot Concepts

### Q: What is Spring Boot? How is it different from Spring?

> "Spring Framework is a comprehensive Java framework — dependency injection, MVC, security, data — but setting it up requires a lot of XML configuration or Java config. It's powerful but verbose.
>
> Spring Boot is an opinionated layer on top of Spring that eliminates most of that setup. Auto-configuration — Spring Boot looks at what's on your classpath and automatically configures beans. If it sees spring-boot-starter-web, it sets up an embedded Tomcat and Jackson. If it sees spring-boot-starter-data-jpa, it sets up Hibernate.
>
> The result is you can go from zero to a running web server in minutes with almost zero configuration."

---

### Q: What is Dependency Injection?

> "Dependency injection means objects don't create their own dependencies — they declare what they need, and something else provides it. In Spring, that 'something else' is the IoC container — Inversion of Control.
>
> In my project, AuthService needs UserRepository, CartRepository, PasswordEncoder, JwtUtil, and AuthenticationManager. Instead of AuthService creating these itself with 'new', I declare them as final fields with @RequiredArgsConstructor — Lombok generates a constructor — and Spring injects them automatically when it creates the AuthService bean.
>
> Constructor injection is the recommended approach over @Autowired on fields — it makes dependencies explicit, enables immutability with final, and makes testing easier."

---

### Q: What is @SpringBootApplication?

> "It's a convenience annotation that combines three annotations. @Configuration marks the class as a source of bean definitions. @EnableAutoConfiguration tells Spring Boot to auto-configure beans based on classpath dependencies. @ComponentScan scans the package and all sub-packages for @Component, @Service, @Repository, @Controller annotations and registers them as beans."

---

### Q: What are Spring profiles?

> "Profiles let you have different configurations for different environments. In my project I have an application-dev.properties with local MySQL credentials and Stripe test keys, and application-prod.properties with Railway environment variables like ${MYSQL_URL}.
>
> I set spring.profiles.active=dev locally. On Railway I set the environment variable SPRING_PROFILES_ACTIVE=prod. Spring automatically loads the right properties file. This way I never hardcode production credentials, and development config doesn't leak into production."

---

### Q: What is @Entity, @Table, @Column?

> "@Entity tells JPA that this Java class maps to a database table. Without it, JPA ignores the class completely.
>
> @Table(name = 'users') specifies which table name to use. I use 'users' instead of 'user' because 'user' is a reserved word in MySQL.
>
> @Column lets me add constraints — nullable = false adds a NOT NULL constraint at the DB level, unique = true creates a unique index, columnDefinition = 'TEXT' changes the column type."

---

### Q: What is @PrePersist and @PreUpdate?

> "These are JPA lifecycle callback annotations. @PrePersist marks a method that runs automatically just before an entity is first inserted into the database. I use it to set createdAt and updatedAt timestamps — so I never have to remember to set them manually.
>
> @PreUpdate runs before every update operation. I use it to refresh the updatedAt timestamp whenever a product is modified."

---

## PART 7 — System Design Questions

### Q: If two customers try to buy the last item at the same time, what happens?

> "This is a classic race condition. In my current implementation, both requests could pass the stock validation check before either one decrements the stock — resulting in negative inventory.
>
> The proper fix is database-level locking. One approach is pessimistic locking — when we load the product for checkout, we add a SELECT FOR UPDATE lock, so only one transaction can hold that product row at a time. In Spring Data I'd use @Lock(LockModeType.PESSIMISTIC_WRITE).
>
> Another approach is optimistic locking with @Version — each product has a version number. If two transactions try to update the same version, only the first succeeds. The second gets an OptimisticLockException and can retry."

---

### Q: How would you scale this application?

> "A few approaches. First, since JWT is stateless, I can run multiple instances behind a load balancer immediately — no session sharing needed.
>
> For the database, I'd add a read replica and route read-only queries there — Spring supports this with @Transactional(readOnly = true).
>
> For frequent reads like product listings, I'd add a Redis cache — cache the paginated product list with a short TTL and invalidate it when an admin updates a product.
>
> For payments and order processing, I'd move to an async queue — RabbitMQ or Kafka — so checkout doesn't block waiting for Stripe. The user gets an immediate response and payment confirmation comes via webhook."

---

### Q: How would you handle forgot password?

> "I'd add an email service. When the user requests a reset, I generate a secure random token, store it in a password_reset_tokens table with the user ID and a short expiry — say 15 minutes. I email the user a link with that token.
>
> When they click the link, they submit a new password. I verify the token exists, hasn't expired, and belongs to their account. Then I hash the new password, update the user, and delete the token so it can't be reused."

---

## PART 8 — Questions They'll Ask About YOU

### Q: Did you write all this code yourself?

**Say this honestly:**
> "I built the project architecture and worked through the implementation. I used documentation — Spring Boot docs, Stripe docs — and debugged issues along the way. The structure, design decisions, and how all the pieces fit together is something I understand deeply."

---

### Q: What was the hardest part to implement?

> "The JWT filter chain was tricky at first. Understanding the exact order filters run in, how the SecurityContext gets populated, and why my filter has to run before UsernamePasswordAuthenticationFilter — getting all of that right took time. Once I understood that Spring Security is just a chain of filters and each filter has a specific job, it clicked."

---

### Q: What would you add if you had more time?

> "A few things. First, email notifications — order confirmation and shipping updates. Second, a proper address system — right now shipping address is just a plain text field, ideally it'd be a separate entity with validation. Third, a product reviews and ratings system. And fourth, better Stripe integration — currently I create a PaymentIntent on checkout, but I'd add Stripe webhooks for refunds and disputes too."

---

### Q: What did you learn building this?

> "I got much deeper on Spring Security — before this I'd used it but didn't really understand how the filter chain works. I also learned a lot about database design — specifically why the price snapshot in OrderItem matters, and why soft delete is important for referential integrity. And working with Stripe taught me how real payment systems avoid handling card data by delegating that entirely to the payment provider."

---

## PART 9 — Quick Fire Round (1-line answers)

| Question | Answer |
|---|---|
| What is ORM? | Maps Java objects to DB tables so you write Java instead of SQL |
| What is Hibernate? | The most popular JPA implementation — turns @Entity into SQL |
| What is a Bean? | An object managed by Spring's IoC container |
| What is @Repository? | Marks a class as a DAO — also enables Spring's exception translation |
| What is @Service? | Marks a class as business logic layer — no technical difference from @Component |
| What is ACID? | Atomicity, Consistency, Isolation, Durability — properties of DB transactions |
| What is a foreign key? | A column that references the primary key of another table |
| What is an index? | A database data structure that speeds up lookups on a column |
| What is N+1 problem? | Loading N parent entities then running N more queries to load children — use JOIN FETCH instead |
| What does @Valid do? | Triggers Bean Validation on the request body — checks @NotNull, @Email, etc. |
| What is CORS? | Cross-Origin Resource Sharing — controls which frontend domains can call your API |
| What is an HTTP header? | Key-value metadata attached to every HTTP request/response |
| What is idempotency? | Calling an endpoint multiple times has the same effect as calling once — GET and PUT are idempotent |
| What is connection pooling? | Reusing DB connections instead of creating new ones per request — Hikari handles this |
| What does @Slf4j do? | Lombok annotation — injects a `log` logger so you can call log.info(), log.error() |

---

## PART 10 — The One Thing to Remember

If you get a question you don't know — say this:

> "I haven't implemented that specific thing in this project, but the way I'd approach it is..."

Then talk through the logic. Interviewers at internship level are not expecting perfection. They're checking if you think like a developer. "I don't know but here's how I'd figure it out" is a strong answer. "I don't know" with nothing after it is the only wrong answer.
