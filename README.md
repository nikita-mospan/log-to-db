# log-to-db
Java library that provides logging API with timings and application call stack.
Logging is done into PostgreSQL database and information can be later conveniently retreived via SQL.

# installation

From the project root directory run the following commands to install library into your local Maven repository.
```sh
mvn clean package
mvn install:install-file -Dfile=target/log-to-db-${version}.jar -DpomFile=pom.xml
```

# Usage

## General notes

Add Maven dependency into your **pom.xml**
```xml
<dependency>
    <groupId>mospan</groupId>
    <artifactId>log-to-db</artifactId>
    <version>${version}</version>
</dependency>
```

Create **src/main/resources/log_datasource.properties** file and specify PostgreSQL database connection configuration:
```
jdbcUrl=jdbc:postgresql://<host>:<port>/<database>?currentSchema=<schema_that_contains_log_tables>
dataSource.user=
```

Create the following log tables in the <schema_that_contains_log_tables>

```sql
drop sequence seq_log_table;
drop table log_table;
drop table log_instances;

create sequence seq_log_table
    start with 1
    no maxvalue
    minvalue 1
    no cycle
    cache 100;

create table log_instances
(
    start_log_id bigserial    not null,
    name         varchar(100) not null,
    start_ts     timestamp(6) not null,
    end_ts       timestamp(6),
    status       varchar(1)   not null,
    constraint c_log_instances_pk primary key (start_log_id),
    constraint c_log_instances_status_chck check (status in ('C'/*completed*/, 'R'/*running*/, 'F'/*failed*/))
);

create index log_instances_name_idx on log_instances (name);

create table log_table
(
    action_name       varchar(64)  not null,
    log_id            bigserial    not null,
    parent_log_id     bigint,
    start_ts          timestamp(6) not null,
    end_ts            timestamp(6),
    status            varchar(1)   not null,
    comments          text,
    exception_message varchar(4000),
    constraint c_log_table_pk primary key (log_id),
    constraint c_log_table_status_chck check (status in ('C'/*completed*/, 'R'/*running*/, 'F'/*failed*/)),
    constraint c_log_table_parent_fk foreign key (parent_log_id) references log_table (log_id)
);

create index log_table_parent_id_idx on log_table (parent_log_id);

create index log_table_action_name_idx on log_table (action_name);
```

Each row in **log_instances** table contains information about a particular high-level process. 
For example this can be instance of single application run.
Rows from **log_table** represent all actions performed by a process. 
These actions form parent child relationship with each other based on callstack 
(if <method A> calls inside itself <methodB> then <method A> is parent and <methodB> is a child).

By knowing start_log_id from **log_instances** table you can obtain logging information via the following SQL:

```sql
WITH RECURSIVE log AS (
    SELECT 1                as level,
           ARRAY [l.log_id] AS path,
           l.log_id,
           l.action_name,
           l.parent_log_id,
           l.start_ts,
           l.end_ts,
           l.status,
           l.comments,
           l.exception_message
    FROM log_table l
    WHERE l.log_id = <start_log_id>
    UNION ALL
    SELECT l.level + 1 as level,
           path || l1.log_id,
           l1.log_id,
           l1.action_name,
           l1.parent_log_id,
           l1.start_ts,
           l1.end_ts,
           l1.status,
           l1.comments,
           l1.exception_message
    FROM log_table l1
             INNER JOIN log l ON l.log_id = l1.parent_log_id
)
SELECT
       lpad(' ', (l.level - 1) * 2) || l.log_id as log_id,
       l.action_name,
       l.start_ts,
       l.end_ts,
       l.end_ts - l.start_ts as duration,
       l.status,
       l.comments,
       l.exception_message
FROM log l
order by l.path, l.start_ts;
```

## Explicit API usage without AspectJ annotations

You can explicitly use logging API from *LogUtils* class:

* *startLog* - creates new logging instance (record in *log_instances* table), initializes logging context
* *openNextLevel* - creates child logging entry (startLog must have been called in current or parent thread)
* *closeLevelSuccess* - set endTimestamp for current logging entry, set status to completed
* *closeLevelFail* - set endTimestamp for current logging entry, set status to failed, save exception stacktrace
* *info* - create logging entry with immediately populated endTimestamp, set status to completed
* *addComment* - add information to *comments* field for current logging entry
* *stopLogSuccess* - set endTimestamp for logging instance, set status to completed, clear logging context
* *stopLogFail* - set endTimestamp for logging instance, set statuc to failed, save exception stacktrace, clear logging context

Example of explicit API:

```java
package example;

import mospan.log_to_db.utils.LogUtils;

public class A {
    public static void main(String[] args) {
        try {
            A a = new A();
            LogUtils.startLog("SampleApplication");
            System.out.println(a.method1("hello"));
            LogUtils.stopLogSuccess();
        } catch (Exception e) {
            LogUtils.stopLogFail(e);
            throw new RuntimeException(e);
        }
    }

    private String method1(String s) {
        try {
            LogUtils.openNextLevel("method1", "Arguments: " + s);
            String result = s + method2(3, 4);
            LogUtils.addComments("\nResult: " + result);
            LogUtils.closeLevelSuccess();
            return result;
        } catch (Exception e) {
            LogUtils.closeLevelFail(e);
            throw new RuntimeException(e);
        }
    }

    private long method2(int i1, int i2) {
        try {
            LogUtils.openNextLevel("method2", "Arguments: " + i1 + ", " + i2);
            long result = i1 + i2;
            LogUtils.addComments("\nResult: " + result);
            LogUtils.info("method2", "Some additional info");
            LogUtils.closeLevelSuccess();
            return result;
        } catch (Exception e) {
            LogUtils.closeLevelFail(e);
            throw new RuntimeException(e);
        }
    }

}
```

After you execute the code above your console log will show you startLogId:
```
[main] INFO  mospan.log_to_db.utils.LogUtils  - startLogId: 6401
```

Using it and SQL query above you get something like this:
|  log_id   | action_name     | start_ts                 | end_ts                    | duration        |  status | comments               | exception_message  |
|---------  |-----------------|--------------------------|---------------------------|-----------------|---------|------------------------|--------------------|
|6401       |SampleApplication|2020-04-26 17:07:12.239000| 2020-04-26 17:07:12.254000|    0.015 secs   |    C    |                        |                    | 
|..6402     | method1         |2020-04-26 17:07:12.248000| 2020-04-26 17:07:12.253000|    0.005 secs   |    C    | Arguments...           |                    |          
|....6403   | method2         |2020-04-26 17:07:12.250000| 2020-04-26 17:07:12.252000|    0.002 secs   |    C    | Arguments...           |                    |          
|......6404 | method2         |2020-04-26 17:07:12.251000| 2020-04-26 17:07:12.252000|    0.001 secs   |    C    | Some additional info...|                    |          

## Implicit API usage with AspectJ annotations

You can mark method with *@LogToDb* annotation. 
In this case method arguments and return value will be logged. 
You can suppress logging them with *suppressLogArgs* and *suppressLogResult* annotation parameters respectively.

You can mark method with *@RootLog* annotation. 
In this case method starts logging new logging instance. 
Its arguments will be logged. 
You can suppress logging them with *suppressLogArgs* annotation parameter.

So the preceding example can be significantly simplified by using annotations:

```java
package example;

import mospan.log_to_db.aspectj.LogToDb;
import mospan.log_to_db.aspectj.RootLog;
import mospan.log_to_db.utils.LogUtils;

public class A {
    @RootLog
    public static void main(String[] args) {
        A a = new A();
        System.out.println(a.method1("hello"));
    }

    @LogToDb
    private String method1(String s) {
        return s + method2(3, 4);
    }

    @LogToDb
    private long method2(int i1, int i2) {
        LogUtils.info("method2", "Some additional info");
        return i1 + i2;
    }

}
```

### Prerequisites for using AspectJ annotations

*@RootLog* and *@LogToDb* use aspectj compile time weaving to add logging logic to the annotated method. 

In order to make this work you have to define aspectj-maven-plugin and add *log-to-db* library as aspectLibrary. 
Additionaly it is better to disable default maven compiler plugin:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.8.1</version>
    <executions>
        <execution>
            <id>default-compile</id>
            <phase>none</phase>
        </execution>
    </executions>
    </plugin>
    <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>aspectj-maven-plugin</artifactId>
        <version>1.11</version>
        <configuration>
            <aspectLibraries>
                <aspectLibrary>
                    <groupId>mospan</groupId>
                    <artifactId>log-to-db</artifactId>
                </aspectLibrary>
            </aspectLibraries>
        </configuration>
        <executions>
            <execution>
                <goals>
                    <goal>compile</goal>
                    <goal>test-compile</goal>
                </goals>
            </execution>
        </executions>
    </plugin>
```

You should also add AspectJ runtime library:

```xml
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjrt</artifactId>
    <version>${aspectj-version}</version>
    <scope>runtime</scope>
</dependency>
```

If your project uses Lombok you will also have to delombok your project via lombok-maven-plugin, 
because Lombok does not support AspectJ compiler:

```xml
<plugin>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok-maven-plugin</artifactId>
    <version>1.18.12.0</version>
    <executions>
    	<execution>
    		<phase>generate-sources</phase>
    		<goals>
    			<goal>delombok</goal>
    		</goals>
    	</execution>
    </executions>
    <configuration>
    	<addOutputDirectory>false</addOutputDirectory>
    	<sourceDirectory>src/main/java</sourceDirectory>
    </configuration>
</plugin>
```

## Contributing

If you'd like to contribute, please fork the repository and use a feature branch. Pull requests are warmly welcomed.

## Licensing

This project is licensed under Unlicense license. 
This license does not require you to take the license with you to your project.
