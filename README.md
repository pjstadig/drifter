# wayfarer

A simple, programmable migrations library.  Wayfarer is (hopefully) the simplest
migration library possible.  It is built around a transactional "store"
abstraction, migrations are data structures (which are store specific), and
wayfarer can be invoked by calling a function to which you give a store and a
list of migrations.

You can extend wayfarer to a new backend by implementing the protocols and
multimethod in `wayfarer.protocol`.  You can even extend it to a
non-transactional store as long as you are OK with the consequences.

I say "store" because wayfarer is not a database migration library.  Wayfarer
can be used to run migrations against Riak or ElasticSearch or an HTTP service.

In a sense wayfarer is a batteries not-included choose-your-own-adventure.  I
will provide some basic tools, and recommendations about how to use them.

## Principles

It may be the case that wayfarer is opinionated.  At the very least, I am
opinionated.  These principles are my opinions shaped by years of writing
applications, and by rubbing elbows with many amazingly talented developers.

In some cases these opinions are enshrined in wayfarer, but even when they are
not you may find yourself fighting against the tool unless you also hold to
them.  If these are unpalatable to you, then I would encourage you to find a
different migration library.

### No "down" migrations

The justification for "down" migrations is that when something goes wrong you
can undo a migration.  While it may seem risky to not have "down" migrations, it
is perhaps just as risky to have them.

"Down" migrations are rarely tested well enough.  In addition, some "up"
migrations are irreversible.  Undoing a migration is not enough, you will also
have to re-deploy the old version of your application, which is also not well
tested and can have unintended consequences.

"Down" migrations also have a very limited lifetime.  You can use them for the
first few hours (maybe even minutes!) after a deployment.  Once the application
has started writing data to the migrated database, then you can lose data by
running a "down" migration, unless you designed the "down" migration to migrate
data from the new columns and types into the old, instead of just dropping the
columns.

No, wayfarer is a "burn the ships" migration library.  There is only forward
progress.  If you are writing an application with expensive and rare
deployments, then this is not the library for you.  If something goes wrong with
a migration, then you may need to quickly write a new forward migration and
immediately deploy it.  However, you should try very hard to avoid this by only
writing well considered and well tested migrations, which you should do even if
you have "down" migrations.

This all may sound risky, but so is trusting in faulty "down" migrations and
rarely used rollback procedures.  If you are still unconvinced, there are other
migration libraries that will support "down" migrations.

### Additive only

Migrations should be additive only.  For a database, this means only ever adding
new tables or new columns to existing tables, but never deleting columns or
table nor changing the types or constraints of existing columns.  You can
imagine the extension of such a concept to other types of stores.

This is related to not having "down" migrations.  If you only ever make additive
changes and you run into some hiccup with a deployment, then you can just
redeploy an old version of the application, and it will work with no need to
undo any migrations.

Migrations should only add new columns that can either be null (preferable) or
have some default value.  This way if you have to rollback to an older version
of the application, and it tries to insert new data, it will not get tripped up
by constraints on new columns that it knows nothing about.

Migrations should never delete columns.  Again, if you deploy an older version
of the application, it should not fail to insert new data because it is trying
to put data into a column or table that no longer exists.

Finally, migrations should not change the meaning of columns (for the same
reasons).  An old version of the application should not fail because a column
changed from being a string into a number.

Code that reads the data store should do a "read repair", reading conditionally
from new columns and falling back to old columns.  As an example, say you have
columns named `first_name` and `last_name` and you decide you would like to
introduce a `full_name` column instead.  When you read data, if the `full_name`
column is not populated, then your data access layer concatenates `first_name`
and `last_name` into the `full_name` field.  Readers would then have access to
the `first_name`, `last_name`, and `full_name` fields with the `full_name` field
reflecting the correct value.

When the data access layer writes it should also concatenate the `first_name`
and `last_name` fields into the `full_name` field so the `full_name` field is
always up-to-date for any older versions of code that might read the data.

Additive migrations are an essential part of zero downtime deployments.  If you
have more than one server behind a load balancer, and you want to do rolling
deploys, then you need to be able to have more than one version of your
application using your data store at the same time.

If this all sounds like a lot of work, then you're right, it is.  It is hard
work to not make a mess of your data access layer by waffling back and forth
about changes to your data model.  Don't do that.

In the end, if you do not have clear ideas about what you want and where you're
going, and if you do not consider your changes carefully, then you'll make a
mess of your code base and your data base, whether your migrations are only
additive or not.

### Specialized syntax

There have been many attempts at creating some kind of cross-database DSL that
can be used to generate SQL statements for many different database systems.  In
my opinion, this is a fool's errand.

The end result will be a lowest common denominator DSL that covers 80% of your
uses cases well and/or a DSL that includes an escape hatch so you can run
arbitrary SQL strings against your database, which just betrays the fact that a
fully cross-database DSL is not possible.

Most applications have no need to talk to more than one database system at the
same time and rarely do they ever switch databases.  Even when you do switch
database systems, the differences will be more than skin deep syntax, and you
will probably have to make changes to account for semantic differences.  You are
adding frustration today for a future (rare) possibility.

In addition, a store need not be a database, and wayfarer does not assume that
it is.  There is no DSL that will unify an RDBMS and Redis.  Even against a
database you may want to run arbitrary Clojure migrations that query some data,
munge it, and reinsert it.  There is no DSL that will unify a SQL DML and
arbitrary Clojure functions.

A wayfarer migration is specialized for a particular backend.  In the case of
the JDBC backend, a migration is a string of arbitrary SQL that is specialized
for the database against which you are running your migrations.  Other backends
may have migrations in other forms, and you need to check the documentation for
the backend you intend to use.

It is not worth trying to create and use a cross-database SQL-like DSL.  It will
give you an unsatisfactory solution to a problem that you don't need to solve,
and it is too narrow a solution for the problem that wayfarer is solving.

### Migrations are data structures (the rest is up to you)

A wayfarer migration is simply a data structure (a map) that gets passed in to
the migration machine.  This means migrations can be constructed and manipulated
in the thousand ways that any other data structure can be.

If you want to use a cross-database DSL, or even a DSL specialized for the
database you are using, then you are free to do so.  As long as the DSL can be
compiled to a string, then that string can be assoc'ed into a map.

You could also read the SQL string from a file, which would allow you to use an
editor that understands SQL to create and edit the file.  Once the migration is
read from the file you just assoc it into a map.

If you have several migrations that are mostly similar, but differ in a few
small ways, then you can write code to generate the migration data structures.

Using data structures as migrations opens up a lot of possibilities, and allows
you to use the same familiar data structure manipulation tools.

## Expected usage

I will give you an example of how I think wayfarer can best be used.  This is
not a mandate, and you are welcome to find some other way that might work better
for you.

Suppose you have an application with a User object and an Article object, each
in its own namespace.  In the user namespace `foo.user` you would have a list of
migrations like so:

```
(def migrations 
  [{:id #uuid "ebbb9b5a-fc77-4b64-9e13-8caf4c17cd8f"
    :migration (string/join " " "CREATE TABLE users("
                                "  id BIGSERIAL PRIMARY KEY,"
                                "  name VARCHAR(2000) NOT NULL UNIQUE,"
                                "  created TIMESTAMP(6) NOT NULL DEFAULT 'now'"
                                ")")}
   {:id #uuid "4b882f52-d722-4bb0-8d09-aa54711837f4" 
    :migration "ALTER TABLE users ADD country CHAR(2)"}])
```

In the `foo.article` namespace you would have a similar list of migrations.

Wayfarer does not provide a leiningen plugin nor a command-line interface, but
it is very simple to write one.  In the `foo.migrate` namespace, you would have
a main function:

```
(defn -main 
  [& args]
  (let [store (wayfarer/init {:backend :jdbc :url "jdbc:postgresql://localhost/foo"})]
    (wayfarer/migrate store (concat user/migrations article/migrations))))

```

This main function can be run with leiningen like so:

```
lein run -m foo.migrate
```

Or using a JAR you have built:

```
java -cp foo-standalone.jar clojure.main -m foo.migrate
```

Or if you've built `foo.migrate` as a generated class:

```
java -cp foo-standalone.jar foo.migrate
```

One hitch with this would be foreign key relationships.  If the `articles` table
has a constraint that references the `users` table, then they must be defined in
a particular order.  You can fix this two ways 1) you can just have a single
list of migrations for the whole application or 2) you could add an additional
list of migrations for adding constraints to you tables and concat it on the
end.

An advantage of this approach is seeing the migrations related to the objects in
their namespace.  A disadvantage is potential VC conflicts over the migration
lists.  This can be mitigated by putting the migrations in files and reading
them in.

## What is missing?

- No cross-database SQL DSL.  At the worst I believe this is a fool's errand,
  and at the best I believe it is orthogonal.
- No CLI.  It is very simple to write your own.

## License

```
Copyright © Paul Stadig.  All rights reserved.

This program and the accompanying materials are made available under the terms
of the Eclipse Public License v1.0 which accompanies this distribution, and is
available at http://www.eclipse.org/legal/epl-v10.html 
```
