fetch("/api/session")
  .then((response) => response.json())
  .then((session) => {
    document.querySelector("#session").textContent = JSON.stringify(session, null, 2);
  })
  .catch((error) => {
    document.querySelector("#session").textContent = "Failed to load session: " + error;
  });
