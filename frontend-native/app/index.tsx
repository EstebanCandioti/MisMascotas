import { Redirect } from "expo-router";
import { useAppData } from "../context/app-data-context";

export default function Index() {
  const { currentUser, ready } = useAppData();

  if (!ready) return null;

  return <Redirect href={currentUser ? "/inicio" : "/login"} />;
}
