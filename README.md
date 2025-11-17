# Yushan Gamification Service

> 🎮 **Gamification Service for Yushan Platform (Phase 2 - Microservices)** - Manages achievements, rewards, leaderboards, and progression systems to create an engaging, game-like reading experience.

## 📋 Overview

Gamification Service is one of the main microservices of Yushan Platform (Phase 2), responsible for managing all gamification features. This service listens to events from User Service and Engagement Service via Kafka to automatically award XP and Yuan to users.

## Architecture Overview

```
┌─────────────────────────────┐
│   Eureka Service Registry   │
│       localhost:8761        │
└──────────────┬──────────────┘
               │
┌──────────────┴──────────────┐
│   Service Registration &     │
│      Discovery Layer         │
└──────────────┬──────────────┘
               │
    ┌──────────┴──────────┬───────────────┬──────────┬──────────┐
    │                     │               │          │          │
    ▼                     ▼               ▼          ▼          ▼
┌────────┐          ┌─────────┐  ┌────────────┐ ┌──────────┐ ┌──────────┐
│  User  │          │ Content │  │ Engagement │ │Gamifica- │ │Analytics │
│Service │◄────────►│ Service │  │  Service   │ │  tion    │ │ Service  │
│ :8081  │          │  :8082  │  │   :8084    │◄┤ Service  │ │  :8083   │
└────────┘          └─────────┘  └────────────┘ │  :8085   │◄└──────────┘
    │                     │              │       └─────┬────┘
    └─────────────────────┴──────────────┴─────────────┘
                    Inter-service Communication
                      (via Feign Clients)
                              │
                    ┌─────────┴─────────┐
                    │   Achievement      │
                    │   Processing &     │
                    │   Reward System    │
                    └───────────────────┘
```

---
## Prerequisites

Before setting up the Gamification Service, ensure you have:
1. **Java 21** installed
2. **Maven 3.8+** or use the included Maven wrapper
3. **Eureka Service Registry** running
4. **PostgreSQL 15+** (for gamification data storage)
5. **Redis** (for caching leaderboards and real-time rankings)

---
## Step 1: Start Eureka Service Registry

**IMPORTANT**: The Eureka Service Registry must be running before starting any microservice.

```bash
# Clone the service registry repository
git clone https://github.com/phutruonnttn/yushan-microservices-service-registry
cd yushan-microservices-service-registry

# Option 1: Run with Docker (Recommended)
docker-compose up -d

# Option 2: Run locally
./mvnw spring-boot:run
```

### Verify Eureka is Running

- Open: http://localhost:8761
- You should see the Eureka dashboard

---

## Step 2: Clone the Gamification Service Repository

```bash
git clone https://github.com/phutruonnttn/yushan-microservices-gamification-service.git
cd yushan-microservices-gamification-service

# Option 1: Run with Docker (Recommended)
docker-compose up -d

# Option 2: Run locally (requires PostgreSQL 15 and Redis to be running beforehand)
./mvnw spring-boot:run
```

---

## Expected Output

### Console Logs (Success)

```
2024-10-16 10:30:15 - Starting GamificationServiceApplication
2024-10-16 10:30:18 - Tomcat started on port(s): 8085 (http)
2024-10-16 10:30:20 - DiscoveryClient_GAMIFICATION-SERVICE/gamification-service:8085 - registration status: 204
2024-10-16 10:30:20 - Started GamificationServiceApplication in 8.8 seconds
```

### Eureka Dashboard

```
Instances currently registered with Eureka:
✅ GAMIFICATION-SERVICE - 1 instance(s)
   Instance ID: gamification-service:8085
   Status: UP (1)
```

---

## API Endpoints

### Health Check
- **GET** `/api/v1/health` - Service health status

### Gamification Stats
- **GET** `/api/v1/gamification/stats/me` - Get current user's gamification stats (level, EXP, Yuan)
- **GET** `/api/v1/gamification/stats/userId/{userId}` - Get other user's gamification stats
- **GET** `/api/v1/gamification/stats/all` - Get all users' gamification stats (for ranking)
- **POST** `/api/v1/gamification/stats/batch` - Get batch users' gamification stats

### User Level
- **GET** `/api/v1/gamification/users/{userId}/level` - Get user's level, EXP, and progress

### Achievements
- **GET** `/api/v1/gamification/achievements/me` - Get current user's unlocked achievements
- **GET** `/api/v1/gamification/achievements/userId/{userId}` - Get other user's unlocked achievements

### Yuan (Virtual Currency)
- **GET** `/api/v1/gamification/yuan/transactions/me` - Get current user's Yuan transaction history (with pagination)

### Rewards
- **POST** `/api/v1/gamification/comments/{commentId}/reward` - Award EXP for creating a comment
- **POST** `/api/v1/gamification/reviews/{reviewId}/reward` - Award EXP for creating a review
- **POST** `/api/v1/gamification/votes/reward` - Award 3 EXP for voting
- **GET** `/api/v1/gamification/votes/check` - Check if user has enough Yuan to vote
- **POST** `/api/v1/gamification/votes/deduct-yuan` - Deduct 1 Yuan for voting

### Admin Endpoints
- **GET** `/api/v1/gamification/admin/yuan/transactions` - Get all Yuan transactions (with filters, ADMIN)
- **POST** `/api/v1/gamification/admin/yuan/add` - Add Yuan to user (ADMIN)

---

## Key Features

### 📊 Experience Points (EXP) & Levels
- Experience points (EXP) system
- Level progression based on EXP
- EXP rewards for:
  - Creating comments
  - Creating reviews
  - Voting for novels (3 EXP per vote)

### 💰 Yuan (Virtual Currency)
- Yuan balance tracking
- Yuan deduction for voting (1 Yuan per vote)
- Yuan transaction history
- Admin can add Yuan to users

### 🏆 Achievement System
- Achievement unlocking system
- User achievement tracking
- Achievement display on profiles

### 🎁 Reward System
- Automatic EXP rewards for user activities
- Vote eligibility checking (requires Yuan)
- Integration with Engagement Service (comments, reviews, votes)

---

## Database Schema

The Gamification Service uses the following key entities:

- **UserPoints** - User EXP, level, and Yuan balance
- **Achievement** - Achievement definitions
- **UserAchievement** - User-achievement mappings
- **YuanTransaction** - Yuan transaction history

---

## Next Steps

Once this basic setup is working:
1. ✅ Create database entities (Achievement, Badge, UserPoints, etc.)
2. ✅ Set up Flyway migrations
3. ✅ Create repositories and services
4. ✅ Implement achievement unlock logic
5. ✅ Set up leaderboard caching with Redis
6. ✅ Add Feign clients for inter-service communication
7. ✅ Implement event listeners for automatic achievement triggers
8. ✅ Set up scheduled jobs for leaderboard updates
9. ✅ Add notification system for unlocked achievements
10. ✅ Implement anti-cheat mechanisms

---

## Troubleshooting

**Problem: Service won't register with Eureka**
- Ensure Eureka is running: `docker ps`
- Check logs: Look for "DiscoveryClient" messages
- Verify defaultZone URL is correct

**Problem: Port 8085 already in use**
- Find process: `lsof -i :8085` (Mac/Linux) or `netstat -ano | findstr :8085` (Windows)
- Kill process or change port in application.yml

**Problem: Database connection fails**
- Verify PostgreSQL is running: `docker ps | grep yushan-postgres`
- Check database credentials in application.yml
- Test connection: `psql -h localhost -U yushan_gamification -d yushan_gamification`

**Problem: Redis connection fails**
- Verify Redis is running: `docker ps | grep redis`
- Check Redis connection: `redis-cli ping`
- Verify Redis host and port in application.yml

**Problem: Build fails**
- Ensure Java 21 is installed: `java -version`
- Check Maven: `./mvnw -version`
- Clean and rebuild: `./mvnw clean install -U`

**Problem: Leaderboard not updating**
- Check scheduled job logs
- Verify Redis cache is working
- Check if background tasks are enabled
- Review leaderboard update interval configuration

**Problem: Achievements not unlocking**
- Check event listener logs
- Verify Feign client connections to other services
- Review achievement criteria logic
- Check database triggers and constraints

---

## Performance Tips
1. **Leaderboard Caching**: Use Redis for frequently accessed leaderboards
2. **Batch Processing**: Process achievement checks in batches
3. **Async Operations**: Use async processing for non-critical updates
4. **Database Indexing**: Index user_id, timestamp, and ranking columns
5. **Rate Limiting**: Implement rate limits on points addition endpoints

---

## Event System
The Gamification Service listens to events from other services:
- **Reading Events**: From Engagement Service (chapters read, time spent)
- **Social Events**: From User Service (follows, reviews, comments)
- **Content Events**: From Content Service (novel ratings)

These events trigger automatic achievement unlocks and point awards.

---

## Inter-Service Communication
The Gamification Service communicates with:
- **User Service**: Fetch user profile data
- **Engagement Service**: Track reading activity
- **Analytics Service**: Send gamification metrics
- **Content Service**: Verify content-related achievements

---

## Security Considerations
- Validate all point additions to prevent cheating
- Implement rate limiting on point-earning endpoints
- Use cryptographic verification for achievement unlocks
- Audit log all reward claims
- Implement cooldown periods for repeatable actions
- Monitor for suspicious activity patterns

---

## Monitoring
The Gamification Service exposes metrics through:
- Spring Boot Actuator endpoints (`/actuator/metrics`)
- Custom gamification metrics (achievements unlocked, points awarded)
- Leaderboard refresh status
- Redis cache hit rates

---

## Anti-Cheat Measures
1. **Rate Limiting**: Prevent rapid repeated actions
2. **Activity Validation**: Verify actions with source services
3. **Pattern Detection**: Monitor for suspicious patterns
4. **Manual Review**: Flag unusual point accumulation
5. **Rollback Capability**: Ability to revert fraudulent gains

---

## 📄 License

This project is part of the Yushan Platform ecosystem.

## 🔗 Links

- **API Gateway**: [yushan-microservices-api-gateway](https://github.com/phutruonnttn/yushan-microservices-api-gateway)
- **Service Registry**: [yushan-microservices-service-registry](https://github.com/phutruonnttn/yushan-microservices-service-registry)
- **Config Server**: [yushan-microservices-config-server](https://github.com/phutruonnttn/yushan-microservices-config-server)
- **Platform Documentation**: [yushan-platform-docs](https://github.com/phutruonnttn/yushan-platform-docs) - Complete documentation for all phases
- **Phase 2 Architecture**: See [Phase 2 Microservices Architecture](https://github.com/phutruonnttn/yushan-platform-docs/blob/main/docs/phase2-microservices/PHASE2_MICROSERVICES_ARCHITECTURE.md)
