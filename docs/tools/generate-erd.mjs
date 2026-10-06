import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const here = path.dirname(fileURLToPath(import.meta.url));
const source = path.resolve(here, '../../backend/src/main/resources/db/migration');
// Preserve SQL string literals while removing comments, then split only at depth zero.
function stripComments(sql) {
  return sql.replace(/'(?:''|[^'])*'|\/\*[\s\S]*?\*\/|--[^\r\n]*/g, s => s.startsWith("'") ? s : ' ');
}
function split(sql, separator) {
  const out = []; let depth = 0, quoted = false, start = 0;
  for (let i = 0; i < sql.length; i++) {
    const c = sql[i];
    if (c === "'") {
      if (quoted && sql[i + 1] === "'") { i++; continue; }
      quoted = !quoted;
    }
    if (quoted) continue;
    if (c === '(') depth++;
    if (c === ')') depth--;
    if (c === separator && depth === 0) { out.push(sql.slice(start, i).trim()); start = i + 1; }
  }
  if (sql.slice(start).trim()) out.push(sql.slice(start).trim());
  return out;
}
const names = s => s.split(',').map(s => s.trim());
const tables = new Map();
const files = fs.readdirSync(source).filter(f => /^V\d+__.*\.sql$/.test(f)).sort((a, b) => Number(a.match(/^V(\d+)/)[1]) - Number(b.match(/^V(\d+)/)[1]));
let createCount = 0, referenceCount = 0, addCount = 0, droppedAddedCount = 0;
const addedColumns = new Set();
function addDefinition(table, text, file) {
  const constraint = text.match(/^CONSTRAINT\s+(\w+)\s+([\s\S]+)$/i);
  if (constraint) { table.constraints.push({ name: constraint[1], sql: constraint[2].replace(/\s+/g, ' '), source: file }); return; }
  const column = text.match(/^(\w+)\s+([\s\S]+)$/);
  assert(column, `Unparsed definition: ${text}`);
  const definition = column[2].replace(/\s+/g, ' ').trim();
  const type = definition.split(/\s+(?=NOT\s+NULL|NULL\b|DEFAULT\b|GENERATED\b|CONSTRAINT\b|PRIMARY\b|UNIQUE\b|REFERENCES\b|CHECK\b)/i)[0];
  assert(!table.columns.some(c => c.name === column[1]), `Duplicate column: ${table.name}.${column[1]}`);
  table.columns.push({ name: column[1], type, definition, nullable: !/\bNOT NULL\b/i.test(definition), source: file, comment: '' });
}
for (const file of files) {
  const sql = stripComments(fs.readFileSync(path.join(source, file), 'utf8'));
  createCount += (sql.match(/\bCREATE TABLE\b/gi) || []).length;
  referenceCount += (sql.match(/\bREFERENCES\s+tb_/gi) || []).length;
  addCount += (sql.match(/\bADD COLUMN\b/gi) || []).length;
  for (const statement of split(sql, ';')) {
    let m;
    if ((m = statement.match(/^CREATE TABLE\s+(\w+)\s*\(([\s\S]*)\)$/i))) {
      const table = { name: m[1], columns: [], constraints: [], indexes: [], comment: '', source: file };
      for (const part of split(m[2], ',')) addDefinition(table, part, file);
      assert(!tables.has(table.name)); tables.set(table.name, table);
    } else if ((m = statement.match(/^ALTER TABLE\s+(\w+)\s+([\s\S]+)$/i))) {
      const table = tables.get(m[1]); assert(table);
      for (const action of split(m[2], ',')) {
        if (/^ADD\s+COLUMN\s+/i.test(action)) {
          addDefinition(table, action.replace(/^ADD\s+COLUMN\s+/i, ''), file);
          addedColumns.add(table.columns.at(-1));
        }
        else if (/^ADD\s+CONSTRAINT\s+/i.test(action)) addDefinition(table, action.replace(/^ADD\s+/i, ''), file);
        else if (/^DROP COLUMN /i.test(action)) {
          const name = action.match(/^DROP COLUMN\s+(\w+)$/i)?.[1];
          const column = table.columns.find(c => c.name === name);
          assert(column, `Unknown column ${action}`);
          if (addedColumns.has(column)) droppedAddedCount++;
          table.columns = table.columns.filter(c => c.name !== name);
        } else if (/^DROP CONSTRAINT /i.test(action)) {
          const name = action.match(/^DROP CONSTRAINT\s+(\w+)$/i)?.[1];
          assert(table.constraints.some(c => c.name === name), `Unknown constraint ${action}`);
          table.constraints = table.constraints.filter(c => c.name !== name);
        } else throw new Error(`Unsupported ALTER: ${action}`);
      }
    } else if ((m = statement.match(/^COMMENT ON (TABLE|COLUMN)\s+(\w+)(?:\.(\w+))?\s+IS\s+'([\s\S]*)'$/i))) {
      const target = m[1].toUpperCase() === 'TABLE' ? tables.get(m[2]) : tables.get(m[2])?.columns.find(c => c.name === m[3]);
      assert(target, `Unknown comment target ${m[2]}.${m[3]}`); target.comment = m[4].replace(/''/g, "'");
    } else if ((m = statement.match(/^CREATE\s+(UNIQUE\s+)?INDEX\s+(\w+)\s+ON\s+(\w+)\s+([\s\S]+)$/i))) {
      tables.get(m[3]).indexes.push({ name: m[2], unique: !!m[1], sql: m[4].replace(/\s+/g, ' '), source: file });
    } else if (!/^(INSERT|UPDATE|DELETE)\b/i.test(statement)) throw new Error(`Unhandled SQL: ${statement.slice(0, 160)}`);
  }
}
const relationships = [];
for (const table of tables.values()) {
  for (const constraint of table.constraints) {
    let m;
    if ((m = constraint.sql.match(/^PRIMARY KEY\s*\(([^)]+)\)/i))) for (const name of names(m[1])) {
      const col = table.columns.find(c => c.name === name); assert(col); col.pk = true; col.nullable = false;
    }
    if ((m = constraint.sql.match(/^UNIQUE\s*\(([^)]+)\)/i))) for (const name of names(m[1])) {
      const col = table.columns.find(c => c.name === name); assert(col); col.unique = true;
    }
    if ((m = constraint.sql.match(/^FOREIGN KEY\s*\(([^)]+)\)\s+REFERENCES\s+(\w+)\s*\(([^)]+)\)(.*)$/i))) {
      const fromColumns = names(m[1]), toColumns = names(m[3]);
      const target = tables.get(m[2]); assert(target, m[2]);
      fromColumns.forEach(n => { const col = table.columns.find(c => c.name === n); assert(col); col.fk = true; });
      toColumns.forEach(n => assert(target.columns.some(c => c.name === n)));
      relationships.push({ name: constraint.name, from: table.name, fromColumns, to: target.name, toColumns, rule: m[4].trim() || 'ON DELETE NO ACTION (기본)', source: constraint.source });
    }
  }
}
assert.equal(tables.size, createCount);
assert.equal(relationships.length, referenceCount);
assert.equal([...tables.values()].reduce((n, t) => n + t.columns.filter(c => addedColumns.has(c)).length, 0), addCount - droppedAddedCount);
assert(tables.get('tb_user').columns.some(c => c.name === 'locked_until'));
assert(tables.get('tb_inbound').columns.some(c => c.name === 'closed_at'));
assert(!tables.get('tb_location').constraints.some(c => c.name === 'uk_location_id'));
const groups = [
  { name: '공통 · 운영', color: '#64748b', tables: ['code_group', 'code', 'menu', 'policy', 'audit_log', 'audit_log_detail', 'upload_history', 'upload_error', 'doc_number', 'notification', 'notification_read'] },
  { name: '조직 · 권한', color: '#6366f1', tables: ['company', 'org', 'user', 'role', 'user_role', 'permission', 'permission_action', 'role_permission', 'role_org_scope'] },
  { name: '거점 · 거래처', color: '#0891b2', tables: ['plant', 'warehouse', 'location', 'partner', 'partner_address'] },
  { name: '상품 · 채널', color: '#0d9488', tables: ['category', 'brand', 'product', 'sku', 'channel', 'channel_sku'] },
  { name: '재고 · 실사', color: '#d97706', tables: ['stock', 'stock_history', 'stock_alloc', 'stock_adjust', 'stock_adjust_line', 'stocktake', 'stocktake_line'] },
  { name: '구매 · 발주', color: '#c026d3', tables: ['purchase_request', 'purchase_request_line', 'purchase_order', 'purchase_order_line'] },
  { name: '입고 · 검수', color: '#e11d48', tables: ['inbound', 'inbound_line', 'inbound_inspect', 'inbound_putaway', 'inbound_correct', 'inbound_correct_line'] },
  { name: '주문', color: '#2563eb', tables: ['order', 'order_line'] },
  { name: '출고 · 피킹', color: '#7c3aed', tables: ['outbound', 'outbound_line', 'outbound_pick'] },
  { name: '패킹 · 배송', color: '#059669', tables: ['pack_box', 'pack_box_line', 'waybill', 'courier', 'delivery_event'] },
];
groups.forEach((g, i) => g.tables.forEach(n => { const t = tables.get('tb_' + n); assert(t, n); t.group = i; }));
assert([...tables.values()].every(t => Number.isInteger(t.group)));
const versionRange = `${files[0].split('__')[0]}–${files.at(-1).split('__')[0]}`;
const data = { tables: [...tables.values()], relationships, groups, files, versionRange, source: `backend/src/main/resources/db/migration · ${versionRange}`, live: false };
const template = fs.readFileSync(path.join(here, 'erd-template.html'), 'utf8');
const output = path.resolve(here, '../erd.html');
fs.writeFileSync(output, template.replace('/*__SCHEMA__*/', () => JSON.stringify(data).replace(/</g, '\\u003c')));
console.log(JSON.stringify({ output, tables: tables.size, columns: data.tables.reduce((n, t) => n + t.columns.length, 0), relationships: relationships.length, addedColumns: addCount }));
