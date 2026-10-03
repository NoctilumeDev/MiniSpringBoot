export function createFeedbackState() {
  return {
    users: { error: null, errorSource: null, notice: null },
    transfer: { error: null, errorSource: null, notice: null },
  };
}

function updateOwner(current, owner, next) {
  return {
    ...current,
    [owner]: { ...current[owner], ...next },
  };
}

export function withError(current, owner, message, source = 'operation', replace = true) {
  if (!replace && (current[owner].error || current[owner].notice)) return current;
  return updateOwner(current, owner, { error: message, errorSource: source, notice: null });
}

export function withNotice(current, owner, message) {
  return updateOwner(current, owner, { error: null, errorSource: null, notice: message });
}

/** 只有产生该错误的操作来源，才有资格清除它。 */
export function clearOwnedError(current, owner, source) {
  if (current[owner].errorSource !== source) return current;
  return updateOwner(current, owner, { error: null, errorSource: null });
}
