# CS122B Project2 - Anver Chou

Youtube Demo Link - [https://youtu.be/ibmsN3zUKE8](https://youtu.be/bUtyGxtPPrs)

LIKE/ILIKE Predicate - Located in MovielistServlet
(DID NOT USE ILIKE)

With SQL LIKE, substring matching was implemented in MovielistServlet. 

// Title substring match
if (title != null) {
    where.add("LOWER(m.title) LIKE CONCAT('%', LOWER(?), '%')");
    params.add(title);
}

// Director substring match
if (director != null) {
    where.add("LOWER(m.director) LIKE CONCAT('%', LOWER(?), '%')");
    params.add(director);
}

// Star name substring match
if (star != null) {
    where.add(
        "EXISTS ("
      + "  SELECT 1 "
      + "  FROM stars_in_movies sim "
      + "  JOIN stars s ON s.id = sim.starId "
      + "  WHERE sim.movieId = m.id "
      + "    AND LOWER(s.name) LIKE CONCAT('%', LOWER(?), '%')"
      + ")"
    );
    params.add(star);
}

// Starts with browse (for prefix)
where.add("LOWER(m.title) LIKE CONCAT(LOWER(?), '%')");
params.add(ch);




          
