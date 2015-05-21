# CHANGELOG

## 0.3.0

* Migrated the data store to Postgres.
* We now cache the pages on table _contents_ when we retrieve them.  This will make the database larger, but allow us to run some experiments without having to re-crawl the entire site.
* Storing a page' description on the pages table so we don't have to parse the HTML every time.
* We can now do text exploration by picking which trope references we like out of a random list. See the README for details.
* Front end is now coded with [re-frame](https://github.com/Day8/re-frame).

## 0.2.0

* All codes are now lowercase. This invalidates the sample database from 20150411.
* Individual create/merge node functions are no longer available outside of tests. Keeping them on the tests since sometimes I want to be able to easily override some default items (like the nextUpdate) during test node creation. 

## 0.1.0

First public release.