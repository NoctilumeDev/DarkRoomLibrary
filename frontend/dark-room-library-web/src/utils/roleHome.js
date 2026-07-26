const ROLE_HOME = Object.freeze({
  0: "/admin",
  1: "/admin",
  2: "/user",
  3: "/procurement",
  4: "/procurement",
});

export function resolveRoleHome(role) {
  if (role === null || role === undefined || role === "") {
    return null;
  }
  return ROLE_HOME[Number(role)] || null;
}
