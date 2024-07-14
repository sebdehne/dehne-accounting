drop table budget;
create table budget
(
    realm_id        text    not null,
    account_id      text    not null,
    month           integer not null,
    amount_in_cents integer not null,
    primary key (realm_id, account_id, month),
    foreign key (realm_id) references realm (id),
    foreign key (account_id) references account (id)
);

drop table budget_history;
create table budget_history
(
    realm_id        text    not null,
    year            integer not null,
    month           integer not null,
    account_id      text    not null,
    amount_in_cents integer not null,
    primary key (realm_id, account_id, year, month),
    foreign key (realm_id) references realm (id),
    foreign key (account_id) references account (id)
);
