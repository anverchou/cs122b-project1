# CS122B Project3 - Anver Chou

Youtube Demo Link - [[https://youtu.be/ibmsN3zUKE8]([https://youtu.be/bUtyGxtPPrs](https://youtu.be/oJCZjePA_fM))
](https://youtu.be/oJCZjePA_fM)

Files that use Prepared Statement:
- src/LoginServlet.java
- src/MovielistServlet.java
- src/SingleMovieServlet.java
- src/SingleStarServlet.java
- src/CartServlet.java
- src/PlaceOrderServlet.java
- src/GenresServlet.java
- src/DashboardAddStarServlet.java
- src/DashboardMetadataServlet.java
- src/EmployeeLoginServlet.java
- src/CsvDataLoader.java
- src/VerifyPassword.java
- src/UpdateSecurePassword.java

1) In memory caching to avoid per-row existence checks. 
Preloads movies, stars, and genres into HashMaps once caching loading begins and then deduplicated memory. This makes it faster by removing thousdands of trips.
2) Batch inserts 
By creating and utilizing batch inserts, the network round trips are drastically reduced to allow MySQL execut bigger insert batches efficiently. 

Inconsitency Report file in repo 
