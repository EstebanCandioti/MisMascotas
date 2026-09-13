import { StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

type Props = {
  name: string;
  breed: string;
  emoji: string;
  color: string;
  onPress?: () => void;
};

export default function PetCard({
  name,
  breed,
  emoji,
  color,
  onPress,
}: Props) {
  return (
    <TouchableOpacity style={styles.card} onPress={onPress}>
      <View style={[styles.avatar, { backgroundColor: color }]}>
        <Text style={styles.emoji}>{emoji}</Text>
      </View>

      <View style={styles.info}>
        <Text style={styles.name}>{name}</Text>
        <Text style={styles.breed}>{breed}</Text>
      </View>

      <Ionicons name="chevron-forward" size={20} color="#A59EAE" />
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    minHeight: 82,
    flexDirection: "row",
    alignItems: "center",
    borderRadius: 17,
    padding: 14,
    marginBottom: 12,
    backgroundColor: "#FFFFFF",
    shadowColor: "#312642",
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 2,
  },

  avatar: {
    width: 52,
    height: 52,
    borderRadius: 26,
    alignItems: "center",
    justifyContent: "center",
    marginRight: 13,
  },

  emoji: {
    fontSize: 26,
  },

  info: {
    flex: 1,
  },

  name: {
    color: "#201B29",
    fontSize: 16,
    fontWeight: "800",
  },

  breed: {
    color: "#837C8D",
    fontSize: 13,
    marginTop: 4,
  },
});