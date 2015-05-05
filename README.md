# Tropology 

Tropology crawls TVTropes.org, converts the relationships between pages into a PostgreSQL database, and helps you visualize relationships between concepts, tropes, creators and material.

This is currently a personal experiment.  Consider it raw, pre-alpha code and likely to change.

Current version is 0.3.0-SNAPSHOT.

[You can read more on our site](http://numergent.com/tags/tropology/).


## Prerequisites

### Clojure

You will need [Leiningen][1] 2.0 or above installed.

[1]: https://github.com/technomancy/leiningen


### PostgreSQL

My current development environment is PostgreSQL 9.4.1. It expects a database called _tropology_ for the dev environment, and _tropology_test_ for the test environment.

There are migration scripts included, but they act only upon the tables and do not create the databases.

See <pre>scripts/create-test-environment.sh</pre> for how I'm creating a Docker container on OS X for tests.

After you have installed PostgreSQL and created the databases, you'll need to run:

    ENV=test lein clj-sql-up migrate
    lein clj-sql-up migrate

### Sample databases

You have two pg_dump'd options if you wish to use a pre-loaded data set for your dev database.

* [Only pages and links](https://mega.co.nz/#!B55wARCQ!H_4sx3jJIUU3Jx2jn5uQdfXcGEe7skCK9STofern7Xk) (116MBs)
* [Full crawled contents](https://mega.co.nz/#!80RyWaiC!N6s2PH7QgwscozsDwv2h8108qlu7wp0Pq2hu6tNj-pc) (3.04GBs)

Import into Postgres using psql as usual.

## Running

To start a web server for the application, run:

    lein cljsbuild once
    lein ring server

Then go to http://localhost:3000/ and enter "Anime/SamuraiFlamenco" on the text box and press *Graph!*.

This will display a (currently somewhat messy) graph of all nodes and related connections.  Clicking any particular node will highlight only that node and its correlated concepts (concepts that both link to the central one and each other).   Double-clicking a node will make a graph out of that node neighborhood.

You can also see the raw data by going to: http://localhost:3000/api/network/Anime/SamuraiFlamenco

## A note on Cursive Clojure

Cursive Clojure does not yet support a way to launch a REPL with specific environment profile. Since the application reads its database connection parameters from the environment configuration, if you start a REPL form Cursive and run the tests against it, you'll be running them against the development database and not the test one.

Make sure you either create a REPL profile specifically for the test settings, or just run the tests via *lein*.

## Next steps

Next up I'll start caching the crawled pages on a local database, that way we can convert and manipulate them without having to re-crawl the site.


## License

TV Tropes content is licensed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License. 

Released under the [Eclipse Public License 1.0](https://tldrlegal.com/license/eclipse-public-license-1.0-(epl-1.0)).

Copyright © 2015 Numergent Limited
