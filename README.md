# Tropology 

Tropology crawls TVTropes.org, converts the relationships between pages into a PostgreSQL database, and helps you visualize relationships between concepts, tropes, creators and material.

This is currently a personal experiment. It'll change, as experiments do, but maybe you'll find use in it as a testing playground.

This is version 1.0.1.

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

### Sample database

[Here you can find a pg_dump'd copy of the fully scanned site](https://mega.co.nz/#!EhZxhBhK!lT38KiMhGxTbjGKD6tJuimc48Tay4ILkEt70evgeM7c). It's 3.22GBs, and includes the entire CC-licensed pages for those matching our crawl settings.

Import into Postgres using psql as usual.

I'm no longer publishing a database version without the contents, since the current visualization and exploration rely on the HTML to extract the reference descriptions.

### Testing

Clojure tests can be run with `lein test`, once the [test database](#postgresql) has been created.  For the ClojureScript tests you'll need to install [PhantomJS 2](http://phantomjs.org/), and run `lein cljsbuild test`.

## A note on Cursive Clojure

Cursive Clojure does not yet support a way to launch a REPL with specific environment profile. Since the application reads its database connection parameters from the environment configuration, if you start a REPL from Cursive and run the tests against it, you'll be running them against the development database and not the test one.

Make sure you either create a REPL profile specifically for the test settings, or just run the tests via *lein*.

## Running

To start a web server for the application, run:

    lein cljsbuild once
    lein ring server

Then go to http://localhost:3000/  See [Using](#Using) below.

## Using 

The core of Tropology is the concept exploration. When you first load it, it will display a random trope reference from the anime series *Samurai Flamenco*. 

You can choose to mark a reference snippet as interesting, which adds it to a list of liked items, or just skip it. If you find a trope mentioned interesting, you can also click on the trope link.  This will load it as the current article to review, as well as add the text snippet to the list of articles you've liked.

You can also click on *Random Article* in order to load any random page from TV Tropes.

Click on *Show* under *Relationship graph* in order to view the relationship between the items you have liked. Clicking on any of the nodes will show the immediately related concepts, and double clicking will load that article for further exploration.

None of this information is currently saved, since I'm only playing with the trope exploration, but that's on my to do list.

**BEWARE: THAR BE SPOILERS**!  I am not yet applying any style that would hide topic spoilers.


## Next steps

Next steps I'm considering are:

* Save a set of references we've found interesting during the exploration stage.
* We're currently showing as possible snippets all *twikilink* elements, but some summary articles use that only to link to other sub-sections and don't contain any actual information.  Consider filtering them out, see if can easily differentiate between those that have content and those that don't.
* Search, to allow you to start your exploration from a preferred topic.  Need to decide if this will happen from a topic title or if I want to add full text search (which would significantly increase the database size).
* Likely a lighter visual theme.


## License

Tropology is released under the [Eclipse Public License 1.0](https://tldrlegal.com/license/eclipse-public-license-1.0-(epl-1.0)).

Includes [Sigma.js](http://sigmajs.org/) for visualization.

TV Tropes content is licensed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License. 

Copyright © 2015 Numergent Limited
