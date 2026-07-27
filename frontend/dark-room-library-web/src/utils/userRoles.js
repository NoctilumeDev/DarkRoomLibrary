export const USER_ROLE = Object.freeze({
  SUPER_ADMIN: 0,
  ADMIN: 1,
  READER: 2,
  ACQUISITIONS: 3,
  LOGISTICS: 4,
});

export const USER_ROLE_OPTIONS = Object.freeze([
  Object.freeze({ value: USER_ROLE.SUPER_ADMIN, label: "超级管理员" }),
  Object.freeze({ value: USER_ROLE.ADMIN, label: "管理员" }),
  Object.freeze({ value: USER_ROLE.READER, label: "读者" }),
  Object.freeze({ value: USER_ROLE.ACQUISITIONS, label: "采购员" }),
  Object.freeze({ value: USER_ROLE.LOGISTICS, label: "物流员" }),
]);

const ROLE_NAMES = Object.freeze(
  Object.fromEntries(USER_ROLE_OPTIONS.map(({ value, label }) => [value, label]))
);

export function getUserRoleName(role) {
  return ROLE_NAMES[Number(role)] || null;
}

export function isAdministratorRole(role) {
  const value = Number(role);
  return value === USER_ROLE.SUPER_ADMIN || value === USER_ROLE.ADMIN;
}
