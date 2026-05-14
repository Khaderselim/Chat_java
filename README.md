# Messenger Chat Application - Database & IDE Setup Guide

## 1. Creating a Schema in SQLPlus and Granting Privileges

### Step 1: Connect to SQLPlus as System Administrator

```bash
sqlplus sys as sysdba
```

You will be prompted to enter the password for the system administrator.

### Step 2: Create a New User/Schema

```sql
CREATE USER messanger IDENTIFIED BY system;
```

### Step 3: Grant Necessary Privileges

Grant the required privileges (CONNECT, CREATE VIEW, and RESOURCE) to the user:

```sql
GRANT CONNECT TO messanger;
GRANT CREATE VIEW TO messanger;
GRANT RESOURCE TO messanger;
```



### Step 4: Verify the User Creation

```sql
SELECT username FROM dba_users WHERE username='MESSANGER';
```

You should see the `MESSANGER` user listed.

### Step 5: Exit SQLPlus

```sql
EXIT;
```

---

## 2. Importing SQL Scripts (GROUP_CHAT.sql and USERS.sql)

### Step 1: Connect as the New Schema User

```bash
sqlplus messanger/system
```

### Step 2: Execute the USERS.sql Script

From the SQLPlus prompt, run:

```sql
@/path/to/USERS.sql
```

Replace `/path/to/USERS.sql` with the actual path to the USERS.sql file. 

**On Windows**, the path might look like:
```sql
@C:\Users\sbize\OneDrive\Documents\github\chat3\MESSANGER\USERS.sql
```

### Step 3: Execute the GROUP_CHAT.sql Script

```sql
@/path/to/GROUP_CHAT.sql
```

Replace `/path/to/GROUP_CHAT.sql` with the actual path to the GROUP_CHAT.sql file.

### Step 4: Verify Table Creation

```sql
DESC USERS;
DESC GROUP_CHAT;
```

Both commands should display the table structures without errors.

### Step 5: Exit SQLPlus

```sql
EXIT;
```

---

## 3. Adding ojdbc11.jar to IntelliJ IDEA

### Method 1: Using Project Structure (Recommended)

1. **Open Project Structure**
   - Go to: `File` → `Project Structure` (or press `Ctrl + Alt + Shift + S`)

2. **Navigate to Libraries**
   - In the left sidebar, click on `Libraries`

3. **Add New Library**
   - Click the `+` button at the top
   - Select `Java`

4. **Locate ojdbc11.jar**
   - Browse to: `chat3\lib\ojdbc11.jar`
   - Click `OK`

5. **Select Modules to Apply**
   - When prompted, select your project module (usually `chat3`)
   - Click `OK`

6. **Apply and Close**
   - Click `Apply`, then `OK` to close the Project Structure dialog


## 4. Testing the Connection

After completing all setup steps, you can test the database connection in your Java code:

```java
import java.sql.Connection;
import java.sql.DriverManager;

public class DatabaseTest {
    public static void main(String[] args) {
        try {
            Class.forName("oracle.jdbc.OracleDriver");
            String url = "jdbc:oracle:thin:@localhost:1521:XE";
            String username = "messanger";
            String password = "system";
            
            Connection conn = DriverManager.getConnection(url, username, password);
            System.out.println("Connection successful!");
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```


---

## 5. Troubleshooting

### Connection Refused Error
- Ensure Oracle Database is running
- Check the hostname, port, and SID are correct (default: `localhost:1521:XE`)

### Table Not Found Error
- Verify that the SQL scripts were executed successfully
- Check that you're connected as the `MESSANGER` user

### ojdbc11.jar Not Found
- Ensure the file exists at: `C:\Users\sbize\OneDrive\Documents\github\chat3\lib\ojdbc11.jar`
- Rebuild the project: `Build` → `Rebuild Project`

### Insufficient Privileges Error
- Ensure all GRANT commands were executed successfully
- Re-run the GRANT commands from Step 3

---

## Summary

1. ✅ Create schema `MESSANGER` in SQLPlus
2. ✅ Grant CONNECT, CREATE VIEW, and RESOURCE privileges
3. ✅ Import USERS.sql and GROUP_CHAT.sql scripts
4. ✅ Add ojdbc11.jar to IntelliJ's project libraries
5. ✅ Test the connection
6. ✅ Start developing!

