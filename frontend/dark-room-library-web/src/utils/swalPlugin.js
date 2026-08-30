let swalPromise;

function loadSwal() {
  if (!swalPromise) {
    swalPromise = Promise.all([
      import("sweetalert2"),
      import("sweetalert2/dist/sweetalert2.min.css"),
      import("@/assets/css/swal-dark.scss"),
    ]).then(([module]) => module.default);
  }
  return swalPromise;
}

export async function swalConfirm(options = {}) {
  const {
    quiet = false,
    customClass = {},
    showClass,
    hideClass,
    ...dialogOptions
  } = options;
  const defaultOptions = {
    title: "提示",
    text: "",
    icon: "info",
    reverseButtons: true,
    showCancelButton: true,
    confirmButtonText: "确认",
    cancelButtonText: "取消",
    customClass: {
      confirmButton: "sweet-btn-primary",
      ...customClass,
    },
    ...(quiet
      ? {
          showClass: { popup: "sweet-popup-enter" },
          hideClass: { popup: "sweet-popup-exit" },
          customClass: {
            popup: "sweet-popup--quiet",
            confirmButton: "sweet-btn-primary",
            ...customClass,
          },
        }
      : {}),
    ...(showClass ? { showClass } : {}),
    ...(hideClass ? { hideClass } : {}),
    ...dialogOptions,
  };

  const result = await swalFire(defaultOptions);
  return result.isConfirmed;
}

export async function swalFire(options = {}) {
  try {
    const Swal = await loadSwal();
    return await Swal.fire(options);
  } catch (error) {
    console.error("Swal Error:", error);
    return { isConfirmed: false, value: false };
  }
}
