<template>
  <v-container fluid>
    <v-row>
      <v-col
        v-for="(word, wordIndex) in words"
        :key="word"
      >
        {{ wordIndex + 1 }}: {{ word }}
      </v-col>
    </v-row>

    <v-row justify="center">
      <v-col cols="4">
        <v-row>
          {{ view.code }}
        </v-row>
        <v-row v-if="actions.available['giveClue']">
          <v-textarea
            v-model="clues"
            solo
            rows="3"
            label="Write your clues here, one clue per line"
          />
        </v-row>
        <v-row v-if="actions.available['guessCode']">
          <v-text-field
            v-model="guess"
            :rules="[v => v.length === 3]"
            counter="3"
            hint="Write the three-digit code you want to guess"
            label="Guess"
          />
        </v-row>
        <v-row>
          <v-btn
            :disabled="!actions.available['giveClue']"
            @click="giveClues"
          >
            Give Clues
          </v-btn>
          <v-btn
            :disabled="!actions.available['guessCode']"
            @click="performGuess"
          >
            Guess
          </v-btn>
        </v-row>
      </v-col>
      <v-col cols="4">
        <v-row>
          <v-textarea
            solo
            rows="5"
            :value="view.teams[view.yourTeam].chat"
          />
        </v-row>
        <v-row>
          <v-text-field
            v-model="chatMessage"
            label="Send a message to your team"
            @keyup.native.enter="sendChat"
          />
        </v-row>
      </v-col>
    </v-row>

    <v-row>
      Current team: {{ view.currentTeam }}
    </v-row>
    <v-row>
      <v-col
        v-for="(team, teamIndex) in view.teams"
        :key="teamIndex"
        cols="6"
      >
        <v-card>
          <v-card-title>
            <PlayerProfile
              v-for="player in team.members"
              :key="player"
              :size="32"
              :context="context"
              :player-index="player"
            />
          </v-card-title>
          <v-card-text>
            <h3>Communications ({{ team.miscommunications }} fails)</h3>
            <div
              v-for="(communication, index) in team.communications"
              :key="index"
            >
              {{ communication }}
            </div>

            <h3>Interceptions ({{ team.intercepted }} successes)</h3>
            <div
              v-for="(communication, index) in team.interceptions"
              :key="index"
            >
              {{ communication }}
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>
<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"

export default {
  name: "Decrypto",
  props: ["view", "actions", "context"],
  components: {
    PlayerProfile
  },
  data() {
    return {
      chatMessage: "",
      clues: "",
      guess: ""
    }
  },
  methods: {
    sendChat() {
      this.actions.actionParameter('chat', this.chatMessage)
    },
    giveClues() {
      this.actions.actionParameter('giveClue', this.clues)
    },
    performGuess() {
      this.actions.actionParameter('guessCode', this.guess)
    }
  },
  computed: {
    words() {
      if (!this.view) return []
      return this.view.words
    }
  }
}
</script>
