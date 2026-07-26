const SPECIAL_CHARACTERS = "!@#$%^&*()_+-=[]{};':\"\\|,.<>/?";

export function getPasswordChecks(password = "") {
  const value = String(password);
  const categories = {
    lower: /[a-z]/.test(value),
    upper: /[A-Z]/.test(value),
    number: /[0-9]/.test(value),
    special: [...value].some((character) => SPECIAL_CHARACTERS.includes(character)),
  };
  const categoryCount = Object.values(categories).filter(Boolean).length;
  const length = value.length >= 8 && value.length <= 20;

  return {
    ...categories,
    length,
    categoryCount,
    valid: length && categoryCount >= 3,
  };
}

export function isStrongPassword(password) {
  return getPasswordChecks(password).valid;
}
