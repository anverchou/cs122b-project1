USE moviedb;

DROP PROCEDURE IF EXISTS add_movie;

DELIMITER $$

CREATE PROCEDURE add_movie(
    IN p_title VARCHAR(100),
    IN p_year INT,
    IN p_director VARCHAR(100),
    IN p_star_name VARCHAR(100),
    IN p_genre_name VARCHAR(32),
    OUT p_message VARCHAR(200)
)
proc: BEGIN

    DECLARE v_movie_num INT DEFAULT 0;
    DECLARE v_star_num INT DEFAULT 0;

    DECLARE v_movie_id VARCHAR(10);
    DECLARE v_existing_movie_id VARCHAR(10);
    DECLARE v_star_id VARCHAR(10);
    DECLARE v_genre_id INT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        INSERT INTO tmp_add_movie_messages(message)
        VALUES ('ERROR: SQL exception occurred. No changes committed.');
        SET p_message = 'ERROR: SQL exception occurred.';
        SELECT message FROM tmp_add_movie_messages;
    END;

    DROP TEMPORARY TABLE IF EXISTS tmp_add_movie_messages;
    CREATE TEMPORARY TABLE tmp_add_movie_messages(
        message VARCHAR(255) NOT NULL
    );

    IF p_title IS NULL OR TRIM(p_title) = '' OR
       p_director IS NULL OR TRIM(p_director) = '' OR
       p_year IS NULL OR p_year <= 0 OR
       p_star_name IS NULL OR TRIM(p_star_name) = '' OR
       p_genre_name IS NULL OR TRIM(p_genre_name) = '' THEN
        INSERT INTO tmp_add_movie_messages(message)
        VALUES ('ERROR: Missing required fields (title, year, director, star, genre).');
        SET p_message = 'ERROR: Missing required fields.';
        SELECT message FROM tmp_add_movie_messages;
        LEAVE proc;
    END IF;

    START TRANSACTION;

    SELECT id INTO v_existing_movie_id
    FROM movies
    WHERE title = p_title AND year = p_year AND director = p_director
    LIMIT 1;

    IF v_existing_movie_id IS NOT NULL THEN
        INSERT INTO tmp_add_movie_messages(message)
        VALUES (CONCAT('Movie already exists (title/year/director match). movieId = ', v_existing_movie_id, '. No changes made.'));
        SET p_message = 'Duplicate movie: already exists.';
        ROLLBACK;
        SELECT message FROM tmp_add_movie_messages;
        LEAVE proc;
    END IF;

    SELECT id INTO v_genre_id
    FROM genres
    WHERE name = p_genre_name
    LIMIT 1;

    IF v_genre_id IS NULL THEN
        INSERT INTO genres(name) VALUES (p_genre_name);
        SET v_genre_id = LAST_INSERT_ID();
        INSERT INTO tmp_add_movie_messages(message)
        VALUES (CONCAT('Created genre "', p_genre_name, '". genreId = ', v_genre_id, '.'));
    ELSE
        INSERT INTO tmp_add_movie_messages(message)
        VALUES (CONCAT('Using existing genre "', p_genre_name, '". genreId = ', v_genre_id, '.'));
    END IF;

    SELECT id INTO v_star_id
    FROM stars
    WHERE name = p_star_name
    LIMIT 1;

    IF v_star_id IS NULL THEN
        SELECT IFNULL(MAX(CAST(SUBSTRING(id, 3) AS UNSIGNED)), 0) + 1
        INTO v_star_num
        FROM stars
        WHERE id LIKE 'nm%';

        SET v_star_id = CONCAT('nm', LPAD(v_star_num, 7, '0'));

        INSERT INTO stars(id, name, birthYear)
        VALUES (v_star_id, p_star_name, NULL);

        INSERT INTO tmp_add_movie_messages(message)
        VALUES (CONCAT('Created star "', p_star_name, '" (birthYear NULL). starId = ', v_star_id, '.'));
    ELSE
        INSERT INTO tmp_add_movie_messages(message)
        VALUES (CONCAT('Using existing star "', p_star_name, '". starId = ', v_star_id, '.'));
    END IF;

    SELECT IFNULL(MAX(CAST(SUBSTRING(id, 3) AS UNSIGNED)), 0) + 1
    INTO v_movie_num
    FROM movies
    WHERE id LIKE 'tt%';

    SET v_movie_id = CONCAT('tt', LPAD(v_movie_num, 7, '0'));

    INSERT INTO movies(id, title, year, director)
    VALUES (v_movie_id, p_title, p_year, p_director);

    INSERT INTO tmp_add_movie_messages(message)
    VALUES (CONCAT('Inserted movie "', p_title, '" (', p_year, ') directed by "', p_director, '". movieId = ', v_movie_id, '.'));

    INSERT INTO genres_in_movies(genreId, movieId)
    VALUES (v_genre_id, v_movie_id);
    INSERT INTO tmp_add_movie_messages(message)
    VALUES (CONCAT('Linked genreId ', v_genre_id, ' to movieId ', v_movie_id, '.'));

    INSERT INTO stars_in_movies(starId, movieId)
    VALUES (v_star_id, v_movie_id);
    INSERT INTO tmp_add_movie_messages(message)
    VALUES (CONCAT('Linked starId ', v_star_id, ' to movieId ', v_movie_id, '.'));

    -- Optional: insert defaults used by Project 2 pages (so the movie renders cleanly)
    INSERT IGNORE INTO ratings(movieId, rating, numVotes)
    VALUES (v_movie_id, 0.0, 0);
    INSERT IGNORE INTO movie_prices(movieId, price)
    VALUES (v_movie_id, 5.99);

    COMMIT;

    SET p_message = CONCAT('SUCCESS: Movie added. movieId = ', v_movie_id);
    INSERT INTO tmp_add_movie_messages(message) VALUES (p_message);

    SELECT message FROM tmp_add_movie_messages;

END$$

DELIMITER ;
