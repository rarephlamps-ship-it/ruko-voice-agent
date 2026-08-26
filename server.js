const express = require('express');
const twilio = require('twilio');

const app = express();
const PORT = process.env.PORT || 3000;

// Use express.urlencoded instead of body-parser
app.use(express.urlencoded({ extended: false }));

app.post('/voice', (req, res) => {
    const twiml = new twilio.twiml.VoiceResponse();
    twiml.say('Hello, this is a Twilio voice response!');
    res.type('text/xml');
    res.send(twiml);
});

app.get('/', (req, res) => {
    res.send('Service is running!');
});

app.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});