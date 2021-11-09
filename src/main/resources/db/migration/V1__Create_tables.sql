create table news
(
    id          bigint primary key auto_increment,
    title       text,
    content     text,
    url         varchar(1000),
    created_at  timestamp,
    modified_at timestamp
);
create Table LINKS_TO_BE_PROCESSED
(
    link varchar(1000)
);

create Table LINKS_ALREADY_PROCESSED
(
    link varchar(1000)
);