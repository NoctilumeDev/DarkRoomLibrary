import {
  getPasswordChecks,
  isStrongPassword,
} from "../../src/utils/passwordRules.js";

describe("password rules", () => {
  it("requires 8-20 characters", () => {
    expect(isStrongPassword("Aa1!abc")).toBe(false);
    expect(isStrongPassword("Aa1!abcd")).toBe(true);
    expect(isStrongPassword(`Aa1!${"x".repeat(18)}`)).toBe(false);
  });

  it("accepts any three of the four supported character classes", () => {
    expect(isStrongPassword("abcDEF12")).toBe(true);
    expect(isStrongPassword("abcdef1!")).toBe(true);
    expect(isStrongPassword("abcdefgh")).toBe(false);
    expect(isStrongPassword("abcdef12")).toBe(false);
  });

  it("recognizes backend-supported special characters", () => {
    const checks = getPasswordChecks("Abcdef1?");
    expect(checks.special).toBe(true);
    expect(checks.categoryCount).toBe(4);
    expect(checks.valid).toBe(true);
  });
});
