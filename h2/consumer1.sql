drop table if exists members;
drop table if exists consumed_zero;
create table members (id varchar primary key, name varchar not null);
create table consumed_zero (producer_id varchar primary key, last_id varchar not null);