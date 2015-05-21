;; migrations/20150422163006286-create-pages.clj

(defn up []
  [(str "CREATE TABLE pages (code varchar(200) primary key, "
        "display varchar(200) not null, "
        "type varchar(100), "
        "title text, category varchar(100), "
        "has_error boolean not null default false, "
        "error text, "
        "is_redirect boolean not null default false, "
        "url text not null, image text, "
        "next_update bigint not null default 0, "
        "time_stamp bigint not null default 0, "
        "outgoing int not null default 0, "
        "incoming int not null default 0, "
        "html text)"
     )])

(defn down []
  ["DROP TABLE pages"])
