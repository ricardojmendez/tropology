# CHANGELOG

## 0.4.0

* New look to help focus on the text exploration.  You can now start off a random article, add references you like, and explore new concepts. Clicking on a link for an article will load its references.
* Updated Clojure to 1.7
* Fixed import issue when dealing with nodes that were potentially marked as redirects. This will likely require a re-crawl.
* If a page is a re-direct, we now track which page it redirects to. 
* API now handles redirects. While exploring we can encounter a link to *Anime/SailorMoon*, which is an article that is only a redirect to *Manga/SailorMoon*. The tropes API now returns the references for the redirected page, not the redirector.
* Further clean up of the data before display - we now parse the _style_ attribute to ensure it is returned as a map and not a string, to avoid React errors.

## 0.3.0

* Migrated the data store to Postgres.
* We now cache the pages on table _contents_ when we retrieve them.  This will make the database larger, but allow us to run some experiments without having to re-crawl the entire site.
* Storing a page's description on the pages table so we don't have to parse the HTML every time.
* We can now do text exploration by picking which trope references we like out of a random list. See the README for details.
* Front end is now coded with [re-frame](https://github.com/Day8/re-frame).
  
## 0.2.0

* All codes are now lowercase. This invalidates the sample database from 20150411.
* Individual create/merge node functions are no longer available outside of tests. Keeping them on the tests since sometimes I want to be able to easily override some default items (like the nextUpdate) during test node creation. 

## 0.1.0

First public release.
 