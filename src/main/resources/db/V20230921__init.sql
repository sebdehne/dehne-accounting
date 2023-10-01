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

create table ledger
(
    id               text    not null,
    name             text    not null,
    description      text,
    bookings_counter integer not null,
    primary key (id)
);

create table user_ledger
(
    user_id      text not null,
    ledger_id    text not null,
    access_level text not null,
    primary key (user_id, ledger_id),
    foreign key (user_id) references user (id),
    foreign key (ledger_id) references ledger (id)
);

create table bank_account
(
    id                            text    not null,
    name                          text    not null,
    description                   text,
    ledger_id                     text    not null,
    bank_id                       text    not null,
    account_number                text    not null,
    open_date                     date    not null,
    close_date                    date,
    open_balance                  integer not null,
    transactions_counter          integer not null,
    transactions_counter_unbooked integer not null,
    current_balance               integer not null,
    primary key (id),
    foreign key (bank_id) references bank (id),
    foreign key (ledger_id) references ledger (id)
);



create table category
(
    id                 text not null,
    name               text not null,
    description        text,
    parent_category_id text,
    primary key (id),
    foreign key (parent_category_id) references category (id)
);

create table category_matcher
(
    id          text not null,
    type        text not null, -- text, regex
    pattern     text not null,
    description text,
    category_id text not null,
    primary key (id),
    foreign key (category_id) references category (id)
);

create table changelog
(
    id                 INTEGER   not null PRIMARY KEY,
    change_type        text      not null,
    change_value       text      not null,
    created            timestamp not null,
    created_by_user_id text      not null,
    FOREIGN KEY (created_by_user_id) REFERENCES user (id)
);

create table booking
(
    ledger_id   text      not null,
    id          integer   not null,
    description text,
    datetime    timestamp not null,
    primary key (ledger_id, id),
    FOREIGN KEY (ledger_id) REFERENCES ledger (id)
);

create table booking_record
(
    ledger_id   text    not null,
    booking_id  text    not null,
    id          integer not null,
    description text,
    category_id text    not null,
    amount      integer not null,

    primary key (ledger_id, booking_id, id),
    FOREIGN KEY (ledger_id, booking_id) REFERENCES booking (ledger_id, id),
    FOREIGN KEY (ledger_id) REFERENCES ledger (id),
    FOREIGN KEY (category_id) REFERENCES category (id)
);

create table bank_transaction
(
    bank_account_id           text      not null,
    id                        INTEGER   not null,
    description               text,
    ledger_id                 text      not null,
    bank_id                   text      not null,
    datetime                  timestamp not null,
    amount                    integer   not null,
    balance                   integer   not null,

    matched_ledger_id         text,
    matched_booking_id        text,
    matched_booking_record_id text,

    primary key (bank_account_id, id),
    foreign key (bank_id) references bank (id),
    foreign key (ledger_id) references ledger (id),
    foreign key (bank_account_id) references bank_account (id),
    foreign key (matched_ledger_id, matched_booking_id, matched_booking_record_id) references booking_record (ledger_id, booking_id, id)
);

insert into user (id, name, description, user_email, active, is_admin)
VALUES ('dbf21a6c-16e2-4689-be24-1766777a70da',
        'Sebastian Dehne',
        null,
        'sebas.dehne@gmail.com',
        1,
        1);