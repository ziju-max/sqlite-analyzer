-- 建表
CREATE TABLE student(id INT, name VARCHAR, age INT);
-- 插入数据
INSERT INTO student(id,name,age) VALUES (1,'Alice',20);
INSERT INTO student(id,name,age) VALUES (2,'Bob',21);
INSERT INTO student(id,name,age) VALUES (3,'Charlie',19);
-- 全表查询
SELECT * FROM student;
-- 条件查询 + 投影
SELECT id,name FROM student WHERE age > 18;
-- 条件查询 + 排序
SELECT id,name,age FROM student WHERE age >= 18 ORDER BY age DESC;
-- 更新
UPDATE student SET age=22 WHERE id=2;
SELECT * FROM student;
-- 删除
DELETE FROM student WHERE id=1;
SELECT * FROM student;
-- 聚合 + 分组
SELECT COUNT(*), AVG(age) FROM student;
SELECT age, COUNT(*) FROM student GROUP BY age;
