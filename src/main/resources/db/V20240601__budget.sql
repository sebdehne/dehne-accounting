create table budget
(
    realm_id        text    not null,
    account_id      text    not null,
    month           integer not null,
    amount_in_cents integer not null,
    PRIMARY KEY (account_id, month),
    foreign key (account_id, realm_id) references account (id, realm_id),
    foreign key (realm_id) references realm (id)
);

create table budget_history
(
    realm_id        text    not null,
    year            integer not null,
    month           integer not null,
    account_id      text    not null,
    amount_in_cents integer not null,
    PRIMARY KEY (realm_id, year, month, account_id),
    foreign key (realm_id) references realm (id)
);

alter table realm
    add column closed_year integer not null default 2023;
alter table realm
    add column closed_month integer not null default 12;

