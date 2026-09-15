-- ============ 错误处理演示 ============
-- 每条 SQL 都会在「对应阶段」报错，但脚本不会中断，会继续执行下一条。
-- 建议跑之前先删除 db.data（rm db.data）保证是全新数据库，效果最清晰。

-- 先建一张表，供后面的错误用例使用
CREATE TABLE err_demo(id INT, name VARCHAR);

-- 错误1：重复建表 → 在「语义分析」阶段报错
CREATE TABLE err_demo(id INT, name VARCHAR);

-- 错误2：查询不存在的表 → 在「语义分析」阶段报错
SELECT * FROM no_such_table;

-- 错误3：查询不存在的列 → 在「语义分析」阶段报错
SELECT password FROM err_demo;

-- 错误4：INSERT 里写了不存在的列 → 在「语义分析」阶段报错
INSERT INTO err_demo(id, wrong_col) VALUES (1, 'x');

-- 错误5：INSERT 值个数和列数不一致 → 在「语义分析」阶段报错
INSERT INTO err_demo(id, name) VALUES (1);

-- 错误6：类型不匹配（id 是 INT，却传字符串）→ 在「执行」阶段报错
INSERT INTO err_demo(id, name) VALUES ('abc', 'x');
