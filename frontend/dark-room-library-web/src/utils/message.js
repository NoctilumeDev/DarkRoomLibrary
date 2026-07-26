let messagePromise;

function loadMessage() {
  if (!messagePromise) {
    messagePromise = import("element-plus/es/components/message/index.mjs").then(
      (module) => module.ElMessage
    );
  }
  return messagePromise;
}

async function showMessage(type, options) {
  const ElMessage = await loadMessage();
  return type ? ElMessage[type](options) : ElMessage(options);
}

function message(options) {
  return showMessage(null, options);
}

["success", "warning", "info", "error", "primary"].forEach((type) => {
  message[type] = (options) => showMessage(type, options);
});

message.closeAll = async () => {
  const ElMessage = await loadMessage();
  ElMessage.closeAll();
};

export default message;
