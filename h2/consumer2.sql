drop table if exists customers;
drop table if exists consumed_zero;
create table customers (id varchar primary key, name varchar not null);
create table consumed_zero (producer_id varchar primary key, last_id varchar not null);