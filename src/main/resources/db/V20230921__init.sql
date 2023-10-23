create table key_value
(
    entry_key     TEXT    not null,
    entry_format  TEXT    not null,
    entry_value   TEXT    not null,
    entry_version INTEGER not null,
    PRIMARY KEY (entry_key)
);

create table bank
(
    id                          text not null,
    name                        text not null,
    description                 text,
    transaction_import_function text,
    primary key (id)
);

create table user
(
    id          text    not null,
    name        text    not null,
    description text,
    user_email  text    not null,
    active      integer not null,
    is_admin    integer not null,
    primary key (user_email)
);
create unique index user_index1 on user (id);

create table realm
(
    id              text    not null,
    name            text    not null,
    description     text,
    currency        text    not null,
    last_booking_id integer not null,
    primary key (id)
);

create table user_realm
(
    user_id      text not null,
    realm_id     text not null,
    access_level text not null,
    primary key (user_id, realm_id),
    foreign key (user_id) references user (id),
    foreign key (realm_id) references realm (id)
);


create table account
(
    realm_id          text not null,
    id                text not null,
    name              text not null,
    description       text,
    parent_account_id text,
    primary key (id),
    foreign key (realm_id) references realm (id),
    foreign key (parent_account_id) references account (id)
);
create unique index account_idx1 on account (realm_id, name, parent_account_id);

create table bank_account
(
    account_id                   text    not null,
    bank_id                      text    not null,
    account_number               text,
    open_date                    date    not null,
    close_date                   date,
    last_unbooked_transaction_id integer not null,

    primary key (account_id),
    foreign key (account_id) references account (id),
    foreign key (bank_id) references bank (id)
);

create table unbooked_bank_transaction
(
    account_id           text      not null,
    realm_id             text      not null,
    id                   integer   not null,
    memo                 text,
    datetime             timestamp not null,
    amount_in_cents      integer   not null,
    other_account_number text,

    primary key (account_id, id),
    foreign key (account_id) references account (id),
    foreign key (realm_id) references realm (id)
);

create table booking
(
    realm_id    text      not null,
    id          integer   not null,
    description text,
    datetime    timestamp not null,
    primary key (realm_id, id),
    FOREIGN KEY (realm_id) REFERENCES realm (id)
);

create table booking_entry
(
    realm_id        text    not null,
    booking_id      integer not null,
    id              integer not null,
    description     text,
    account_id      text    not null,
    amount_in_cents integer not null,

    primary key (realm_id, booking_id, id),
    FOREIGN KEY (realm_id, booking_id) REFERENCES booking (realm_id, id),
    FOREIGN KEY (realm_id) REFERENCES realm (id),
    FOREIGN KEY (account_id) REFERENCES account (id)
);

create table unbooked_bank_transaction_matcher
(
    realm_id  text      not null,
    id        text      not null,
    json      text      not null,
    last_used timestamp not null,

    primary key (id),
    foreign key (realm_id) references realm (id)
);

create table user_state
(
    id            text      not null,
    user_id       text      not null,
    user_state    text      not null,
    last_modified timestamp not null,

    primary key (user_id)
);

create table user_state_session
(
    id            text      not null,
    user_state_id text      not null,
    last_used     timestamp not null,
    primary key (id)
);


