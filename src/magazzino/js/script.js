// script.js

// Funzione per mostrare le sezioni
function showSection(sectionId) {
  const sections = document.querySelectorAll('.section');
  sections.forEach(section => {
    section.classList.remove('active');
  });
  document.getElementById(sectionId).classList.add('active');

  if (sectionId === 'magazzino') {
    loadMagazzinoData();
  } else if (sectionId === 'prodottiInScadenza') {
    loadProdottiInScadenzaData();
  } else if (sectionId === 'daOrdinare') {
    loadDaOrdinareData();
  }
}

// Funzione per cambiare le citazioni sugli animali
const quotes = [
  "Gli animali sono amici completi: non fanno domande e non criticano.",
  "La grandezza di un uomo si puo' giudicare dal modo in cui tratta gli animali.",
  "La compassione verso gli animali è intimamente legata alla bontà del carattere.",
  "Gli animali riempiono la vita di amore e lealtà.",
  "Un mondo senza animali sarebbe un luogo senza anima.",
  "Gli occhi di un animale riflettono la purezza del cuore.",
  "La compagnia di un animale rende ogni giorno speciale.",
  "Gli animali parlano un linguaggio che arriva dritto al cuore.",
  "La felicità è un cucciolo che ti corre incontro.",
  "Gli animali sono amici silenziosi ma dalle grandi lezioni.",
  "Il legame con un animale è un tesoro inestimabile.",
  "Gli animali ci insegnano cosa significa amare senza condizioni.",
  "Ogni animale è un piccolo miracolo della natura."
];

let currentQuoteIndex = 0;
function changeQuote() {
  const quoteElement = document.getElementById('quoteText');
  quoteElement.textContent = quotes[currentQuoteIndex];
  currentQuoteIndex = (currentQuoteIndex + 1) % quotes.length;
}

setInterval(changeQuote, 10000); // Cambia citazione ogni 10 secondi

// Funzione per caricare i dati del magazzino
function loadMagazzinoData() {
  fetch('/riepilogoMagazzino')
    .then(response => response.json())
    .then(data => {
      const tableBody = document.getElementById('productTableBody');
      let rowsHtml = '';

      data.forEach(prodotto => {
        // Formatta la data di scadenza in un formato leggibile
        const scadenzaFormattata = prodotto.scadenza ? new Date(prodotto.scadenza).toLocaleDateString('it-IT') : 'N/A';

        // Gestisce gli apici singoli nel nome del prodotto
        const nomeProdottoEscaped = escapeHtml(prodotto.nome);

        rowsHtml += `
          <tr>
            <td>${nomeProdottoEscaped}</td>
            <td>${prodotto.quantita}</td>
            <td>${scadenzaFormattata}</td>
            <td>
              <button onclick="prelevaProdotto(${prodotto.id}, ${prodotto.quantita}, '${nomeProdottoEscaped}')">Preleva</button>
              <button onclick="eliminaProdotto(${prodotto.id})">Elimina</button>
            </td>
          </tr>
        `;
      });

      tableBody.innerHTML = rowsHtml;
    })
    .catch(error => {
      console.error('Errore nel recupero dei dati del magazzino:', error);
    });
}

// Funzione per gestire i caratteri speciali nel nome del prodotto
function escapeHtml(text) {
  const map = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": "\\'",
    "/": '&#x2F;'
  };
  return text.replace(/[&<>"'/]/g, function(m) { return map[m]; });
}

// Funzione per caricare i prodotti in scadenza
function loadProdottiInScadenzaData() {
  const giorniScadenza = document.getElementById('giorniScadenza').value;

  fetch(`/prodottiInScadenza?giorni=${encodeURIComponent(giorniScadenza)}`)
    .then(response => response.json())
    .then(data => {
      const tableBody = document.getElementById('expiringProductsTableBody');
      let rowsHtml = '';

      data.forEach(prodotto => {
        const scadenzaFormattata = prodotto.scadenza ? new Date(prodotto.scadenza).toLocaleDateString('it-IT') : 'N/A';
        const nomeProdottoEscaped = escapeHtml(prodotto.nome);

        rowsHtml += `
          <tr>
            <td>${nomeProdottoEscaped}</td>
            <td>${prodotto.quantita}</td>
            <td>${scadenzaFormattata}</td>
          </tr>
        `;
      });

      tableBody.innerHTML = rowsHtml;
    })
    .catch(error => {
      console.error('Errore nel recupero dei prodotti in scadenza:', error);
    });
}

// Funzione per caricare i prodotti da ordinare
function loadDaOrdinareData() {
  fetch('/prodottiDaOrdinare')
    .then(response => response.json())
    .then(data => {
      const tableBody = document.getElementById('daOrdinareTableBody');
      let rowsHtml = '';

      data.forEach(prodotto => {
        const nomeProdottoEscaped = escapeHtml(prodotto.nome);

        rowsHtml += `
          <tr>
            <td>${nomeProdottoEscaped}</td>
            <td>${prodotto.quantita}</td>
          </tr>
        `;
      });

      tableBody.innerHTML = rowsHtml;
    })
    .catch(error => {
      console.error('Errore nel recupero dei prodotti da ordinare:', error);
    });
}

// Funzione per prelevare un prodotto
function prelevaProdotto(idProdotto, quantitaDisponibile, nomeProdotto) {
  const modal = document.getElementById('prelevaModal');
  const closeBtn = document.getElementById('closePrelevaModal');
  const confermaBtn = document.getElementById('confermaPrelevaButton');
  const quantitaInput = document.getElementById('quantitaDaPrelevare');
  const prodottoNomeElem = document.getElementById('prelevaProdottoNome');

  prodottoNomeElem.textContent = `Prodotto: ${nomeProdotto} (Disponibile: ${quantitaDisponibile})`;
  quantitaInput.value = '';
  modal.style.display = 'block';

  closeBtn.onclick = () => {
    modal.style.display = 'none';
  };

  confermaBtn.onclick = () => {
    const quantitaDaPrelevare = parseInt(quantitaInput.value, 10);

    if (isNaN(quantitaDaPrelevare) || quantitaDaPrelevare <= 0) {
      alert('Per favore inserisci una quantità valida.');
      return;
    }

    if (quantitaDaPrelevare > quantitaDisponibile) {
      alert('Quantità richiesta non disponibile in magazzino.');
      return;
    }

    // Invia la richiesta al server per prelevare il prodotto
    fetch('/prelevaProdotto', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: `id=${encodeURIComponent(idProdotto)}&quantita=${encodeURIComponent(quantitaDaPrelevare)}`,
    })
      .then(response => {
        if (response.ok) {
          alert('Prodotto prelevato con successo');
          modal.style.display = 'none';
          loadMagazzinoData(); // Aggiorna la tabella
        } else {
          response.text().then(text => {
            alert('Errore nel prelievo del prodotto: ' + text);
          });
        }
      })
      .catch(error => {
        console.error('Errore durante il prelievo del prodotto:', error);
        alert('Errore durante il prelievo del prodotto.');
      });
  };

  // Chiude il modale quando l'utente clicca fuori dal contenuto
  window.onclick = function(event) {
    if (event.target == modal) {
      modal.style.display = 'none';
    }
  };
}

// Funzione per eliminare un prodotto
function eliminaProdotto(idProdotto) {
  if (!confirm('Sei sicuro di voler eliminare questo prodotto?')) {
    return;
  }

  fetch('/eliminaProdotto', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: `id=${encodeURIComponent(idProdotto)}`,
  })
    .then(response => {
      if (response.ok) {
        alert('Prodotto eliminato con successo');
        loadMagazzinoData(); // Aggiorna la tabella
      } else {
        response.text().then(text => {
          alert('Errore nell\'eliminazione del prodotto: ' + text);
        });
      }
    })
    .catch(error => {
      console.error('Errore durante l\'eliminazione del prodotto:', error);
      alert('Errore durante l\'eliminazione del prodotto.');
    });
}

// Inizializzazione quando la pagina viene caricata
window.onload = function() {
  changeQuote();
  // Se desideri caricare altre informazioni all'avvio, puoi farlo qui
};


