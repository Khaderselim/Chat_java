create table GROUP_CHAT
(
    ID       NUMBER not null
        primary key,
    GID      VARCHAR2(255),
    NAME     VARCHAR2(255),
    FILENAME VARCHAR2(255),
    CREATOR  VARCHAR2(255),
    MEMBERS  VARCHAR2(255)
)
/

