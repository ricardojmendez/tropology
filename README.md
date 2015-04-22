# Tropology 

Tropology crawls TVTropes.org, converts the relationships between pages into a Neo4j database, and helps you visualize relationships between concepts, tropes, creators and material.

This is currently a personal experiment.  Consider it raw, pre-alpha code and likely to change.

Current version is 0.1.0

[You can read more on our site](http://numergent.com/tags/tropology/).


## Prerequisites

### Clojure

You will need [Leiningen][1] 2.0 or above installed.

[1]: https://github.com/technomancy/leiningen


### Neo4j

My current development environment is Neo4j 2.2.  The tests are run against a Neo4j node responding on localhost on port 7373 (instead of 7474, to keep it running in parallel with a dev environment).

See <pre>scripts/create-test-environment.sh</pre> for how I'm creating a Docker container on OS X for tests.

If you create a database from scratch using a Docker container, you may want to create the following indices up front:

    CREATE CONSTRAINT on (p:Article) ASSERT p.code IS UNIQUE; 
    CREATE INDEX ON :Article(incoming);
    CREATE INDEX ON :Article(outgoing);
    CREATE INDEX ON :Article(nextUpdate);

You can also bootstrap it with [a pre-loaded Neo4j database from this location](https://mega.co.nz/#!w8J3QDjT!eJ4sgDUEyvHd0CL6wbQoQNuZFjq5u33EVPhx50nZVeg).

## Running

To start a web server for the application, run:

    lein cljsbuild once
    lein ring server

Then go to http://localhost:3000/ and enter "Anime/SamuraiFlamenco" on the text box and press *Graph!*.

This will display a (currently somewhat messy) graph of all nodes and related connections.  Clicking any particular node will highlight only that node and its correlated concepts (concepts that both link to the central one and each other).   Double-clicking a node will make a graph out of that node neighborhood.

You can also see the raw data by going to: http://localhost:3000/api/network/Anime/SamuraiFlamenco

## Next steps

Next up I'll start caching the crawled pages on a local database, that way we can convert and manipulate them without having to re-crawl the site.


## License

TV Tropes content is licensed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License. 

Released under the [Eclipse Public License 1.0](https://tldrlegal.com/license/eclipse-public-license-1.0-(epl-1.0)).

Copyright © 2015 Numergent Limited
