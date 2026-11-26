import assert from 'node:assert/strict';
import { test } from 'node:test';
import { normalizeInterval, readEventMessage } from '../src/streamData.js';

test('only supports the default or a positive signed 32-bit interval', () => {
  assert.equal(normalizeInterval(''), '');
  assert.equal(normalizeInterval('003'), '3');
  assert.equal(normalizeInterval('2147483647'), '2147483647');
  for (const value of ['0', '-1', '1.5', '2147483648', 'NaN', 'Infinity', '1e2', '0x10', '   ', '12\n']) {
    assert.equal(normalizeInterval(value), null, value);
  }
});

test('malformed events and events without a string message leave the last message intact', () => {
  assert.equal(readEventMessage('{"message":"hello"}'), 'hello');
  for (const value of ['{', 'null', '{}', '{"message":2}']) {
    assert.equal(readEventMessage(value), null, value);
  }
});
