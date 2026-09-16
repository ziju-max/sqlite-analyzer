# -- 建表
# CREATE TABLE student(id INT, name VARCHAR(30), age INT);
# -- 插入数据
# INSERT INTO student(id,name,age) VALUES (1,'Alice',20);
# INSERT INTO student(id,name,age) VALUES (2,'Bob',21);
# INSERT INTO student(id,name,age) VALUES (3,'Charlie',19);
# -- 全表查询
# SELECT * FROM student;
# -- 条件查询 + 投影
# SELECT id,name FROM student WHERE age > 18;
# -- 条件查询 + 排序
# SELECT id,name,age FROM student WHERE age >= 18 ORDER BY age DESC;
# -- 更新
# UPDATE student SET age=22 WHERE id=2;
# SELECT * FROM student;
# -- 删除
# DELETE FROM student WHERE id=1;
# SELECT * FROM student;
# -- 聚合 + 分组
# SELECT COUNT(*), AVG(age) FROM student;
# SELECT age, COUNT(*) FROM student GROUP BY age;
CREATE TABLE student (
     student_id INT PRIMARY KEY,
     name VARCHAR(30) NOT NULL,
     age INT,
     gender CHAR(1),
     major VARCHAR(50)
);

CREATE TABLE course (
    course_id INT PRIMARY KEY,
    course_name VARCHAR(50) NOT NULL,
    credit INT,
    teacher VARCHAR(30)
);
CREATE TABLE enrollment (
    enrollment_id INT PRIMARY KEY,
    student_id INT,
    course_id INT,
    score INT,
    semester VARCHAR(20)
);

INSERT INTO student VALUES(1, '张伟', 18, 'M', '计算机科学'),
    (2, '李娜', 19, 'F', '软件工程');
INSERT INTO student VALUES(3, '王强', 20, 'M', '计算机科学');
INSERT INTO student VALUES(4, '赵敏', 21, 'F', '数据科学');
INSERT INTO student VALUES(5, '刘洋', 19, 'M', '软件工程');
INSERT INTO student VALUES (6, '陈静', 22, 'F', '人工智能');
INSERT INTO student  VALUES(7, '杨帆', 20, 'M', '网络工程');
INSERT INTO student  VALUES(8, '黄婷', 18, 'F', '计算机科学');
INSERT INTO student  VALUES(9, '周杰', 23, 'M', '数据科学');
INSERT INTO student  VALUES(10, '吴倩', 21, 'F', '人工智能');
INSERT INTO student VALUES(11, '徐磊', 19, 'M', '网络工程');
INSERT INTO student  VALUES(12, '孙丽', 20, 'F', '软件工程');
INSERT INTO student VALUES (13, '胡明', 22, 'M', '计算机科学');
INSERT INTO student VALUES(14, '郭芳', 18, 'F', '人工智能');
INSERT INTO student  VALUES (15, '何军', 24, 'M', '数据科学');
INSERT INTO student  VALUES(16, '马超', 21, 'M', '网络工程');
INSERT INTO student  VALUES(17, '唐雪', 20, 'F', '软件工程');
INSERT INTO student  VALUES(18, '罗成', 22, 'M', '计算机科学');
INSERT INTO student  VALUES (19, '郑爽', 19, 'F', '数据科学');
INSERT INTO student  VALUES (20, '林峰', 23, 'M', '人工智能');
#course
INSERT INTO course VALUES (101, '数据库原理', 4, '张老师');

INSERT INTO course VALUES (102, 'Java程序设计', 4, '李老师');

INSERT INTO course VALUES (103, 'Python程序设计', 3, '王老师');

INSERT INTO course VALUES (104, '计算机网络', 4, '赵老师');

INSERT INTO course VALUES (105, '操作系统', 4, '刘老师');

INSERT INTO course VALUES (106, '数据结构', 4, '陈老师');

INSERT INTO course VALUES (107, '人工智能导论', 3, '杨老师');

INSERT INTO course VALUES (108, 'Web开发', 3, '黄老师');

INSERT INTO course VALUES (109, '软件工程', 2, '周老师');

INSERT INTO course VALUES (110, '机器学习', 4, '吴老师');

#enrollment

INSERT INTO enrollment VALUES (1, 1, 101, 85, '2025-2026-1');

INSERT INTO enrollment VALUES (2, 1, 102, 92, '2025-2026-1');

INSERT INTO enrollment VALUES (3, 1, 106, 88, '2025-2026-1');

INSERT INTO enrollment VALUES (4, 2, 101, 90, '2025-2026-1');

INSERT INTO enrollment VALUES (5, 2, 103, 95, '2025-2026-1');

INSERT INTO enrollment VALUES (6, 2, 107, 89, '2025-2026-1');

INSERT INTO enrollment VALUES (7, 3, 102, 78, '2025-2026-1');

INSERT INTO enrollment VALUES (8, 3, 104, 82, '2025-2026-1');

INSERT INTO enrollment VALUES (9, 3, 106, 91, '2025-2026-1');

INSERT INTO enrollment VALUES (10, 4, 103, 88, '2025-2026-1');

INSERT INTO enrollment VALUES (11, 4, 107, 94, '2025-2026-1');

INSERT INTO enrollment VALUES (12, 4, 110, 91, '2025-2026-1');

INSERT INTO enrollment VALUES (13, 5, 102, 86, '2025-2026-1');

INSERT INTO enrollment VALUES (14, 5, 106, 80, '2025-2026-1');

INSERT INTO enrollment VALUES (15, 5, 108, 90, '2025-2026-1');

INSERT INTO enrollment VALUES (16, 6, 101, 96, '2025-2026-1');

INSERT INTO enrollment VALUES (17, 6, 107, 93, '2025-2026-1');

INSERT INTO enrollment VALUES (18, 6, 110, 97, '2025-2026-1');

INSERT INTO enrollment VALUES (19, 7, 104, 75, '2025-2026-1');

INSERT INTO enrollment VALUES (20, 7, 105, 83, '2025-2026-1');

INSERT INTO enrollment VALUES (21, 7, 109, 88, '2025-2026-1');

INSERT INTO enrollment VALUES (22, 8, 101, 91, '2025-2026-1');

INSERT INTO enrollment VALUES (23, 8, 106, 87, '2025-2026-1');

INSERT INTO enrollment VALUES (24, 8, 108, 93, '2025-2026-1');

INSERT INTO enrollment VALUES (25, 9, 103, 82, '2025-2026-1');

INSERT INTO enrollment VALUES (26, 9, 107, 85, '2025-2026-1');

INSERT INTO enrollment VALUES (27, 9, 110, 90, '2025-2026-1');

INSERT INTO enrollment VALUES (28, 10, 101, 89, '2025-2026-1');

INSERT INTO enrollment VALUES (29, 10, 105, 92, '2025-2026-1');

INSERT INTO enrollment VALUES (30, 10, 110, 95, '2025-2026-1');

INSERT INTO enrollment VALUES (31, 11, 104, 81, '2025-2026-1');

INSERT INTO enrollment VALUES (32, 11, 109, 86, '2025-2026-1');

INSERT INTO enrollment VALUES (33, 12, 102, 93, '2025-2026-1');

INSERT INTO enrollment VALUES (34, 12, 108, 89, '2025-2026-1');

INSERT INTO enrollment VALUES (35, 13, 101, 84, '2025-2026-1');

INSERT INTO enrollment VALUES (36, 13, 106, 90, '2025-2026-1');

INSERT INTO enrollment VALUES (37, 14, 107, 96, '2025-2026-1');

INSERT INTO enrollment VALUES (38, 14, 110, 94, '2025-2026-1');

INSERT INTO enrollment VALUES (39, 15, 103, 79, '2025-2026-1');

INSERT INTO enrollment VALUES (40, 15, 105, 85, '2025-2026-1');

#词法分析

#SELECT $ FROM student;

#SELECT * FROM student /* comment*/

#SELECT * FROM student WHERE name = '张伟;

#SELECT * FROM student WHERE age = 12.3.4;

#语法分析

#SELECT * FROM student select student_id from student;

#SELECT * student;

#SELECT * FROM;


#SELECT name, age, FROM student;

#INSERT INTO student VALUES (21, '测试', 20, 'M', '计算机科学',);


#语义分析

#INSERT INTO student VALUES (21, '测试', 'abc', 'M', '计算机科学');

#SELECT * FROM students;

#SELECT username FROM student;

#SELECT * FROM student WHERE age >'18';

#
SELECT * FROM student;

SELECT student_id, name, age FROM student;

SELECT * FROM student WHERE age >= 20;

SELECT * FROM student WHERE age >= 20 AND gender = 'M';

SELECT * FROM student WHERE age < 20 OR major = '人工智能';

SELECT * FROM student
WHERE age >= 20 OR gender ='M' AND major = '计算机科学';

SELECT * FROM course ORDER BY credit DESC;

SELECT COUNT(score)
FROM enrollment;

SELECT
    COUNT(score),
    SUM(score),
    AVG(score),
    MAX(score),
    MIN(score)
FROM enrollment;




