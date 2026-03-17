# CS122B Project4 - Anver Chou

Youtube Demo Link - [[https://youtu.be/1WZyj0L2GZA]([https://youtu.be/bUtyGxtPPrs](https://youtu.be/oJCZjePA_fM))
](https://youtu.be/oJCZjePA_fM)

Only part 1 of the project is shown in the demo. I was unsuccessful in completing the JMeter demo.

- # Connection Pooling
    - #### Include the filename/path of all code/configuration files in GitHub of using JDBC Connection Pooling.
      - WebContent/META-INF/context.xml
        Define the Tomcat JDBC pool Resources to enable prepared statement caching
      - WebContent/WEB-INF/web.xml
        Declare entries so servlets can look up pool

- All services that access MySQL and JNDI DataSource instead of DriverManager:
src/login.LoginServlet.java
src/EmployeeLoginServlet.java
src/movies.MovielistServlet.java
src/movies.AutocompleteServlet.java
src/movies.SingleMovieServlet.java
src/SingleStarServlet.java
src/movies.GenresServlet.java
src/movies.CartServlet.java
src/movies.PlaceOrderServlet.java
src/DashboardAddMovieServlet.java
src/DashboardAddStarServlet.java
src/DashboardMetadataServlet.java

    - #### Explain how Connection Pooling is utilized in the Fabflix code.
      - Tomcat is managing a pool of Db connections defined in the context.xml as a JNDI Resource
      - Each servlet performs a one-time lookup in init():
      - For each request, the servlet obtains a connection with: try (Connection conn = dataSource.getConnection())
      - When the try-with-resources closes, the connection is returned to the pool instead of being destroyed

    - #### Explain how Connection Pooling works with two backend SQL.
      Fablix defines two separate connection pools, each pointing to a different MySQL Server. Each Tomcat backend loads both DataSources and the servlet chooses which pool to use depending on whether it is a read or write request. Reads pull from slave, writes go to master. 

- # Master/Slave
    - #### Include the filename/path of all code/configuration files in GitHub for routing queries to Master/Slave SQL.
        - WebContent/META-INF/context.xml
          both DataSources: jdbc/moviedb_master and jdbc/moviedb_slave
         - WebContent/WEB-INF/web.xml
           resource ref declarations for jdbc/moviedb_master and jdbc/moviedb_slave
          - src/movies.MovielistServlet.java
- All servlets that use the database and need correct routing:
src/movies.AutocompleteServlet.java
src/movies.SingleMovieServlet.java
src/SingleStarServlet.java
src/movies.GenresServlet.java
src/login.LoginServlet.java / src/EmployeeLoginServlet.java
src/movies.PlaceOrderServlet.java
src/DashboardAddMovieServlet.java
src/DashboardAddStarServlet.java
    - #### How read/write requests were routed to Master/Slave SQL?
Any servlets that perform inserts, updates, deletes, or calls stored procedures that modify data would send WRITE requests to master only. 
Any servlets that only perform SELECT would send READ requests to slave only. 
