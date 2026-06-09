-- P8: text2SQL Demo Schema
-- 员工管理系统：5 张表 + 示例数据
-- 执行方式：psql -h localhost -U postgres -d postgres -f init-demo-schema.sql

CREATE TABLE IF NOT EXISTS departments (
    dept_id     SERIAL PRIMARY KEY,
    dept_name   VARCHAR(100) NOT NULL UNIQUE,
    location    VARCHAR(100),
    budget      DECIMAL(12, 2),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS employees (
    emp_id      SERIAL PRIMARY KEY,
    emp_name    VARCHAR(100) NOT NULL,
    email       VARCHAR(100) UNIQUE,
    dept_id     INTEGER REFERENCES departments(dept_id),
    hire_date   DATE,
    title       VARCHAR(50),
    manager_id  INTEGER REFERENCES employees(emp_id),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS salaries (
    salary_id   SERIAL PRIMARY KEY,
    emp_id      INTEGER REFERENCES employees(emp_id),
    amount      DECIMAL(10, 2) NOT NULL,
    effective_date DATE NOT NULL,
    end_date    DATE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS projects (
    project_id  SERIAL PRIMARY KEY,
    project_name VARCHAR(100) NOT NULL,
    dept_id     INTEGER REFERENCES departments(dept_id),
    start_date  DATE,
    end_date    DATE,
    status      VARCHAR(20) CHECK (status IN ('active', 'completed', 'cancelled')),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS employee_projects (
    emp_id      INTEGER REFERENCES employees(emp_id),
    project_id  INTEGER REFERENCES projects(project_id),
    role        VARCHAR(50),
    assigned_date DATE DEFAULT CURRENT_DATE,
    PRIMARY KEY (emp_id, project_id)
);

-- Sample data
INSERT INTO departments (dept_name, location, budget) VALUES
    ('Engineering', 'Building A', 5000000.00),
    ('Marketing', 'Building B', 2000000.00),
    ('Human Resources', 'Building C', 1500000.00),
    ('Sales', 'Building D', 3000000.00),
    ('Finance', 'Building E', 2500000.00)
ON CONFLICT (dept_name) DO NOTHING;

INSERT INTO employees (emp_name, email, dept_id, hire_date, title, manager_id) VALUES
    ('Alice Zhang', 'alice.zhang@company.com', 1, '2020-03-15', 'Senior Engineer', NULL),
    ('Bob Li', 'bob.li@company.com', 1, '2021-06-01', 'Engineer', 1),
    ('Carol Wang', 'carol.wang@company.com', 1, '2022-01-10', 'Junior Engineer', 1),
    ('David Chen', 'david.chen@company.com', 2, '2019-08-20', 'Marketing Manager', NULL),
    ('Eve Liu', 'eve.liu@company.com', 2, '2021-03-05', 'Marketing Specialist', 4),
    ('Frank Wu', 'frank.wu@company.com', 3, '2018-11-12', 'HR Director', NULL),
    ('Grace Huang', 'grace.huang@company.com', 4, '2020-07-22', 'Sales Manager', NULL),
    ('Henry Xu', 'henry.xu@company.com', 4, '2021-09-18', 'Sales Representative', 7),
    ('Ivy Sun', 'ivy.sun@company.com', 5, '2019-04-30', 'Finance Manager', NULL),
    ('Jack Zhou', 'jack.zhou@company.com', 1, '2023-02-14', 'Intern', 1)
ON CONFLICT (email) DO NOTHING;

INSERT INTO salaries (emp_id, amount, effective_date) VALUES
    (1, 150000.00, '2024-01-01'),
    (2, 120000.00, '2024-01-01'),
    (3, 90000.00, '2024-01-01'),
    (4, 140000.00, '2024-01-01'),
    (5, 100000.00, '2024-01-01'),
    (6, 130000.00, '2024-01-01'),
    (7, 135000.00, '2024-01-01'),
    (8, 85000.00, '2024-01-01'),
    (9, 125000.00, '2024-01-01'),
    (10, 50000.00, '2024-01-01')
ON CONFLICT DO NOTHING;

INSERT INTO projects (project_name, dept_id, start_date, status) VALUES
    ('AI Platform', 1, '2024-01-01', 'active'),
    ('Brand Refresh', 2, '2024-02-01', 'active'),
    ('ERP Upgrade', 5, '2023-06-01', 'completed'),
    ('Q4 Sales Push', 4, '2024-10-01', 'active'),
    ('Mobile App', 1, '2024-03-15', 'active')
ON CONFLICT DO NOTHING;

INSERT INTO employee_projects (emp_id, project_id, role) VALUES
    (1, 1, 'Tech Lead'),
    (2, 1, 'Developer'),
    (3, 5, 'Developer'),
    (4, 2, 'Project Owner'),
    (5, 2, 'Coordinator'),
    (7, 4, 'Project Owner'),
    (8, 4, 'Sales Support'),
    (10, 5, 'Intern')
ON CONFLICT DO NOTHING;