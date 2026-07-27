import { USER_ROLE } from "@/utils/userRoles.js";

const ROLE_HOME = Object.freeze({
  [USER_ROLE.SUPER_ADMIN]: "/admin",
  [USER_ROLE.ADMIN]: "/admin",
  [USER_ROLE.READER]: "/user",
  [USER_ROLE.ACQUISITIONS]: "/procurement",
  [USER_ROLE.LOGISTICS]: "/procurement",
});

export function resolveRoleHome(role) {
  if (role === null || role === undefined || role === "") {
    return null;
  }
  return ROLE_HOME[Number(role)] || null;
}
