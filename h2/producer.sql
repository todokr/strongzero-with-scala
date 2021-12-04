drop table if exists users;
drop table if exists produced_zero;
create table users (id varchar primary key, name varchar not null);
create table produced_zero (id varchar primary key, type varchar not null, message blob not null);